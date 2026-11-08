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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID

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

    private fun service() = ReceiptService(QrTokenService(qrRepo, props, clock), receipts, storage, { i, m ->
        ocr.extract(i, m)
    }, InboxService(inboxRepo, props, clock), props, clock)

    private val image = "fake-image-bytes".toByteArray()

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

    override fun exists(vehicleId: UUID, sha256: String) = rows.any { it.vehicleId == vehicleId && it.sha256 == sha256 }

    override fun insert(r: ReceiptRecord) {
        rows += r
    }

    override fun deleteExpired(now: Instant) = emptyList<String>()
}

private class MemStorage : ReceiptStorage {
    val objects = mutableMapOf<String, ByteArray>()

    override fun put(key: String, bytes: ByteArray, contentType: String) {
        objects[key] = bytes
    }

    override fun signedUrl(key: String) = "signed://$key"

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
