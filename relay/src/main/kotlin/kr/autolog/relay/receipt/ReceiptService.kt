package kr.autolog.relay.receipt

import kr.autolog.relay.common.ApiException
import kr.autolog.relay.common.RelayProperties
import kr.autolog.relay.common.Tokens
import kr.autolog.relay.inbox.InboxService
import kr.autolog.shared.OcrStatus
import kr.autolog.shared.ReceiptFields
import kr.autolog.shared.ReceiptPayload
import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionOperations
import java.time.Clock
import java.time.Duration
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * 영수증 파이프라인 (F-41, 기술명세 4.5).
 * QR 토큰 소모 → 중복 확인 → Gemini OCR → 마스킹·검산 → 원본·썸네일(EXIF 회전 반영) 저장
 * → receipts 기록 + 차 inbox(RECEIPT).
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
    private val tx: TransactionOperations,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun handleUpload(vehicleCode: String, token: String?, image: ByteArray, mimeType: String): ReceiptPayload {
        if (image.isEmpty() || !mimeType.startsWith("image/")) {
            throw ApiException(HttpStatus.BAD_REQUEST, "RECEIPT_NOT_IMAGE", "이미지 파일만 올릴 수 있습니다")
        }
        val vehicleId = qr.consume(vehicleCode, token)
        val sha = Tokens.sha256Hex(image)
        if (repo.exists(vehicleId, sha)) throw duplicate()

        val now = clock.instant()
        val receiptId = UUID.randomUUID()

        val ocrResult =
            runCatching { ocr.extract(image, mimeType) }
                .onFailure { log.warn("영수증 OCR 실패 receipt={} : {}", receiptId, it.message) }
                .getOrNull()
        val fields = ocrResult?.fields?.let(ReceiptVerifier::mask)
        val arithOk = fields?.let(ReceiptVerifier::arithmeticOk)
        val status = if (ocrResult != null) OcrStatus.OK else OcrStatus.FAILED
        val thumb = Thumbnailer.jpeg(image)
        if (thumb == null) log.warn("영수증 이미지를 읽지 못함 receipt={} type={} → 원본을 썸네일로 사용", receiptId, mimeType)

        val originalKey = "$vehicleId/$receiptId/original"
        val thumbKey = if (thumb != null) "$vehicleId/$receiptId/thumb.jpg" else originalKey
        val stored = mutableListOf<String>()
        try {
            storage.put(originalKey, image, mimeType)
            stored += originalKey
            if (thumb != null) {
                storage.put(thumbKey, thumb, JPEG)
                stored += thumbKey
            }
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
            // receipts 행과 inbox 편지는 함께 들어가거나 함께 빠진다.
            tx.executeWithoutResult {
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
                inbox.deliver(vehicleId, payload)
            }
            return payload
        } catch (e: Exception) {
            // 저장해 둔 파일이 행 없이 남지 않게 지운다.
            stored.forEach { key -> runCatching { storage.delete(key) }.onFailure { log.warn("파일 정리 실패 {}", key) } }
            // 같은 사진이 동시에 두 번 올라오면 exists 검사를 둘 다 통과하고 유니크 인덱스에서 걸린다.
            if (e is DuplicateKeyException) throw duplicate()
            throw e
        }
    }

    /** 30일 지난 영수증 원본·썸네일과 행 삭제 (매일 04:30 KST). */
    @Scheduled(cron = "0 30 4 * * *", zone = "Asia/Seoul")
    fun purgeExpired() {
        val keys = repo.deleteExpired(clock.instant())
        keys.forEach { key -> runCatching { storage.delete(key) }.onFailure { log.warn("영수증 파일 삭제 실패 {}", key) } }
        if (keys.isNotEmpty()) log.info("영수증 만료 삭제 파일 {}개", keys.size)
    }

    private fun duplicate() = ApiException(HttpStatus.CONFLICT, "RECEIPT_DUPLICATE", "이미 등록된 영수증입니다")

    private companion object {
        const val JPEG = "image/jpeg"
        val EMPTY_FIELDS = ReceiptFields(null, null, null, null, null, null, null, null)
    }
}
