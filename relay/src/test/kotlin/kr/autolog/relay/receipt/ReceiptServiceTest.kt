package kr.autolog.relay.receipt

import kr.autolog.relay.common.ApiException
import kr.autolog.relay.common.RelayProperties
import kr.autolog.relay.inbox.InboxRepository
import kr.autolog.relay.inbox.InboxRow
import kr.autolog.relay.inbox.InboxService
import kr.autolog.relay.support.TestClock
import kr.autolog.shared.AutoLogJson
import kr.autolog.shared.InboxPayload
import kr.autolog.shared.OcrStatus
import kr.autolog.shared.ReceiptFields
import kr.autolog.shared.ReceiptPayload
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.dao.DuplicateKeyException
import org.springframework.transaction.support.TransactionOperations
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.UUID
import javax.imageio.ImageIO

class ReceiptServiceTest {
    private val clock = TestClock()
    private val props = RelayProperties()
    private val vehicleId = UUID.randomUUID()
    private val qrRepo = OneTokenRepo(vehicleId)
    private val receipts = MemReceipts()
    private val storage = MemStorage()
    private val inboxRepo = MemInbox()
    private var ocr: ReceiptOcr =
        ReceiptOcr { _, _ ->
            OcrResult(ReceiptFields("SK", null, "휘발유", 40.0, 1700, 0, 68_000, "5678-1234"), mapOf("amountWon" to 0.97))
        }

    private fun service() = ReceiptService(
        QrTokenService(qrRepo, props, clock),
        receipts,
        storage,
        { i, m -> ocr.extract(i, m) },
        InboxService(inboxRepo, props, clock, listOf(ReceiptThumbUrlRefresher(receipts, storage))),
        props,
        clock,
        TransactionOperations.withoutTransaction(),
    )

    private val image = "fake-image-bytes".toByteArray()

    /** 흰 바탕 800x400 JPEG. */
    private fun realJpeg(): ByteArray = ByteArrayOutputStream().also {
        val img = BufferedImage(800, 400, BufferedImage.TYPE_INT_RGB)
        img.createGraphics().apply {
            color = Color.WHITE
            fillRect(0, 0, 800, 400)
            dispose()
        }
        ImageIO.write(img, "jpg", it)
    }.toByteArray()

    @Test
    fun `업로드하면 OCR 결과가 마스킹·검산되어 차 inbox에 들어간다`() {
        val payload = service().handleUpload("car1", "tok", image, "image/jpeg")

        assertEquals(OcrStatus.OK, payload.ocrStatus)
        assertEquals("1234", payload.fields.cardLast4)
        assertEquals(true, payload.arithOk)
        val delivered = AutoLogJson.decodeFromString(InboxPayload.serializer(), inboxRepo.rows.single().payloadJson)
        assertEquals(payload, delivered)
        assertTrue(storage.objects.keys.any { it.endsWith("/original") })
    }

    @Test
    fun `OCR이 실패해도 썸네일만 담아 차로 보낸다`() {
        ocr = ReceiptOcr { _, _ -> error("Gemini 다운") }

        val payload = service().handleUpload("car1", "tok", image, "image/jpeg")

        assertEquals(OcrStatus.FAILED, payload.ocrStatus)
        assertNull(payload.fields.amountWon)
        assertTrue(payload.thumbUrl.startsWith("signed://"))
        assertEquals(1, inboxRepo.rows.size)
    }

    @Test
    fun `같은 차에 같은 사진을 다시 올리면 409`() {
        service().handleUpload("car1", "tok", image, "image/jpeg")
        qrRepo.reset()

        val e = assertThrows<ApiException> { service().handleUpload("car1", "tok", image, "image/jpeg") }

        assertEquals("RECEIPT_DUPLICATE", e.code)
    }

    @Test
    fun `동시에 같은 사진이 올라와 유니크 인덱스에 걸리면 409이고 파일을 지운다`() {
        receipts.failInsertWith = DuplicateKeyException("uq receipts(vehicle_id, sha256)")

        val e = assertThrows<ApiException> { service().handleUpload("car1", "tok", realJpeg(), "image/jpeg") }

        assertEquals("RECEIPT_DUPLICATE", e.code)
        assertTrue(storage.objects.isEmpty(), "남은 파일: ${storage.objects.keys}")
        assertTrue(inboxRepo.rows.isEmpty())
    }

    @Test
    fun `DB 기록이 실패하면 저장한 원본과 썸네일을 지운다`() {
        receipts.failInsertWith = IllegalStateException("DB 다운")

        assertThrows<IllegalStateException> { service().handleUpload("car1", "tok", realJpeg(), "image/jpeg") }

        assertTrue(storage.objects.isEmpty(), "남은 파일: ${storage.objects.keys}")
    }

    @Test
    fun `차가 inbox를 가져갈 때마다 썸네일 URL을 새로 서명한다`() {
        service().handleUpload("car1", "tok", realJpeg(), "image/jpeg")
        storage.signVersion = 2

        val item = InboxService(inboxRepo, props, clock, listOf(ReceiptThumbUrlRefresher(receipts, storage)))
            .pending(vehicleId)
            .single()

        assertTrue((item.payload as ReceiptPayload).thumbUrl.endsWith("?v=2"))
    }

    @Test
    fun `이미지가 아니면 토큰을 쓰기 전에 거부한다`() {
        assertThrows<ApiException> { service().handleUpload("car1", "tok", "x".toByteArray(), "application/pdf") }

        assertEquals(false, qrRepo.used)
    }

    @Test
    fun `QR 토큰이 틀리면 401`() {
        val e = assertThrows<ApiException> { service().handleUpload("car1", "wrong", image, "image/jpeg") }

        assertEquals("QR_TOKEN_INVALID", e.code)
    }
}

private class OneTokenRepo(private val vehicleId: UUID) : QrTokenRepository {
    var used = false
    private val validHash = kr.autolog.relay.common.Tokens.sha256Hex("tok")

    fun reset() {
        used = false
    }

    override fun insert(tokenHash: String, vehicleId: UUID, expiresAt: Instant) = Unit

    override fun consume(vehicleCode: String, tokenHash: String, now: Instant): UUID? =
        if (!used && tokenHash == validHash) {
            used = true
            vehicleId
        } else {
            null
        }

    override fun isValid(vehicleCode: String, tokenHash: String, now: Instant) = !used && tokenHash == validHash
}

private class MemReceipts : ReceiptRepository {
    val rows = mutableListOf<ReceiptRecord>()
    var failInsertWith: Exception? = null

    override fun exists(vehicleId: UUID, sha256: String) = rows.any { it.vehicleId == vehicleId && it.sha256 == sha256 }

    override fun insert(r: ReceiptRecord) {
        failInsertWith?.let { throw it }
        rows += r
    }

    override fun thumbKey(receiptId: UUID, vehicleId: UUID) =
        rows.firstOrNull { it.receiptId == receiptId && it.vehicleId == vehicleId }?.thumbKey

    override fun deleteExpired(now: Instant) = emptyList<String>()
}

private class MemStorage : ReceiptStorage {
    val objects = mutableMapOf<String, ByteArray>()
    var signVersion = 1

    override fun put(key: String, bytes: ByteArray, contentType: String) {
        objects[key] = bytes
    }

    override fun signedUrl(key: String) = "signed://$key?v=$signVersion"

    override fun delete(key: String) {
        objects.remove(key)
    }
}

private class MemInbox : InboxRepository {
    val rows = mutableListOf<InboxRow>()

    override fun insert(
        vehicleId: UUID,
        kind: String,
        payloadJson: String,
        createdAt: Instant,
        expiresAt: Instant,
    ): Long {
        rows += InboxRow(rows.size + 1L, vehicleId, kind, payloadJson, createdAt)
        return rows.size.toLong()
    }

    override fun deletePending(vehicleId: UUID, kind: String) = 0

    override fun findPending(vehicleId: UUID, now: Instant) = rows.toList()

    override fun delete(id: Long, vehicleId: UUID) = false

    override fun touchLastInbox(vehicleId: UUID, at: Instant) = Unit

    override fun deleteExpired(now: Instant) = 0
}
