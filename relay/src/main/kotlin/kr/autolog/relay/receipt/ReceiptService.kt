package kr.autolog.relay.receipt

import kr.autolog.relay.common.ApiException
import kr.autolog.relay.common.RelayProperties
import kr.autolog.relay.common.Tokens
import kr.autolog.relay.inbox.InboxService
import kr.autolog.shared.OcrStatus
import kr.autolog.shared.ReceiptFields
import kr.autolog.shared.ReceiptPayload
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * 영수증 파이프라인 (F-41, 기술명세 4.5).
 * QR 토큰 소모 → 중복 확인 → 원본·썸네일 저장 → Gemini OCR → 마스킹·검산 → receipts 기록 → 차 inbox(RECEIPT).
 * OCR이 실패해도 썸네일만 담아 차로 보낸다(차에서 수동 입력).
 */
@Service
class ReceiptService(
    private val qr: QrTokenService,
    private val repo: ReceiptRepository,
    private val storage: ReceiptStorage,
    private val ocr: ReceiptOcr,
    private val inbox: InboxService,
    private val props: RelayProperties,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun handleUpload(vehicleCode: String, token: String?, image: ByteArray, mimeType: String): ReceiptPayload {
        if (image.isEmpty() || !mimeType.startsWith("image/")) {
            throw ApiException(HttpStatus.BAD_REQUEST, "RECEIPT_NOT_IMAGE", "이미지 파일만 올릴 수 있습니다")
        }
        val vehicleId = qr.consume(vehicleCode, token)
        val sha = Tokens.sha256Hex(image)
        if (repo.exists(vehicleId, sha)) {
            throw ApiException(HttpStatus.CONFLICT, "RECEIPT_DUPLICATE", "이미 등록된 영수증입니다")
        }

        val now = clock.instant()
        val receiptId = UUID.randomUUID()
        val originalKey = "$vehicleId/$receiptId/original"
        storage.put(originalKey, image, mimeType)
        val thumb = Thumbnailer.jpeg(image)
        val thumbKey = if (thumb !=
            null
        ) {
            "$vehicleId/$receiptId/thumb.jpg".also { storage.put(it, thumb, "image/jpeg") }
        } else {
            originalKey
        }

        val ocrResult =
            runCatching { ocr.extract(image, mimeType) }
                .onFailure { log.warn("영수증 OCR 실패 receipt={} : {}", receiptId, it.message) }
                .getOrNull()
        val fields = ocrResult?.fields?.let(ReceiptVerifier::mask)
        val arithOk = fields?.let(ReceiptVerifier::arithmeticOk)
        val status = if (ocrResult != null) OcrStatus.OK else OcrStatus.FAILED

        repo.insert(
            ReceiptRecord(
                receiptId = receiptId,
                vehicleId = vehicleId,
                sha256 = sha,
                storageKey = originalKey,
                thumbKey = thumbKey,
                takenAt = now,
                ocrStatus = status.name,
                fields = fields,
                confidence = ocrResult?.confidence.orEmpty(),
                arithOk = arithOk,
                expiresAt = now.plus(Duration.ofDays(props.receiptRetentionDays)),
            ),
        )
        val payload =
            ReceiptPayload(
                receiptId = receiptId.toString(),
                sha256 = sha,
                thumbUrl = storage.signedUrl(thumbKey),
                takenAt = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(now.atOffset(ZoneOffset.ofHours(9))),
                fields = fields ?: EMPTY_FIELDS,
                confidence = ocrResult?.confidence.orEmpty(),
                arithOk = arithOk,
                ocrStatus = status,
            )
        inbox.deliver(vehicleId, payload)
        return payload
    }

    /** 30일 지난 영수증 원본·썸네일과 행 삭제 (매일 04:30 KST). */
    @Scheduled(cron = "0 30 4 * * *", zone = "Asia/Seoul")
    fun purgeExpired() {
        val keys = repo.deleteExpired(clock.instant())
        keys.forEach { key -> runCatching { storage.delete(key) }.onFailure { log.warn("영수증 파일 삭제 실패 {}", key) } }
        if (keys.isNotEmpty()) log.info("영수증 만료 삭제 파일 {}개", keys.size)
    }

    private companion object {
        val EMPTY_FIELDS = ReceiptFields(null, null, null, null, null, null, null, null)
    }
}
