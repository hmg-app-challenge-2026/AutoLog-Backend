package kr.autolog.vehicle.domain

import kotlinx.coroutines.test.runTest
import kr.autolog.shared.PlaceKind
import kr.autolog.vehicle.core.ConfirmResult
import kr.autolog.vehicle.core.ExpenseSource
import kr.autolog.vehicle.core.PlaceInfo
import kr.autolog.vehicle.core.QrResult
import kr.autolog.vehicle.core.TripSummary
import kr.autolog.vehicle.core.VoiceFuel
import kr.autolog.vehicle.fake.FakeRelayClient
import kr.autolog.vehicle.fake.InMemoryDomainStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptServiceTest {
    private val store = InMemoryDomainStore()
    private val relay = FakeRelayClient()
    private val service = ReceiptService(store, relay)
    private val r = receipt()

    @Test
    fun `QR은 매번 서버에서 새로 받는다`() = runTest {
        service.issueQr()
        val qr = service.issueQr()

        assertTrue(qr is QrResult.Issued)
        assertEquals(2, relay.qrCalls)
    }

    @Test
    fun `서버 호출이 예외를 던져도 Failed로 돌려준다`() = runTest {
        val broken = ReceiptService(
            store,
            object : kr.autolog.vehicle.core.RelayClient {
                override suspend fun issueQrToken(): QrResult = error("timeout")

                override suspend fun syncNow() = error("timeout")
            },
        )

        assertTrue(broken.issueQr() is QrResult.Failed)
    }

    @Test
    fun `후보는 저장소의 운행과 주유소에서 찾는다`() = runTest {
        store.placeList += PlaceInfo("gs", "주유소", PlaceKind.GAS_STATION, 37.40, 127.10)
        store.tripList += TripSummary("t-1", "car-1", null, kst(9), kst(9, 30), endLat = 37.40, endLng = 127.10)

        val c = service.candidates(r)

        assertEquals("t-1", c.single().tripId)
        assertTrue(c.single().hasFuelStop)
    }

    @Test
    fun `확정하면 비용을 저장하고 ack한다`() = runTest {
        val edits = r.fields.copy(liters = 40.0, cardLast4 = "카드 1234-****-****-5678")

        val result = service.confirm(r, "t-1", edits)

        assertTrue(result is ConfirmResult.Saved)
        val e = store.expenses.single()
        assertEquals("t-1", e.tripId)
        assertEquals(68_000, e.amountWon)
        assertEquals("5678", e.cardLast4)
        assertEquals(setOf("cardLast4"), e.editedFields)
        assertEquals(kst(9, 20), e.occurredAt)
        assertEquals(listOf("r-1"), store.acked)
    }

    @Test
    fun `검산이 맞지 않으면 저장하지 않는다`() = runTest {
        val result = service.confirm(r, "t-1", r.fields.copy(amountWon = 80_000))

        assertTrue(result is ConfirmResult.Rejected)
        assertTrue(store.expenses.isEmpty())
    }

    @Test
    fun `금액이 없으면 거부`() = runTest {
        assertTrue(service.confirm(r, null, r.fields.copy(amountWon = null)) is ConfirmResult.Rejected)
    }

    @Test
    fun `같은 영수증은 두 번 저장하지 않는다`() = runTest {
        service.confirm(r, null, r.fields)

        val again = service.confirm(r, null, r.fields)

        assertTrue(again is ConfirmResult.Rejected)
        assertEquals(1, store.expenses.size)
    }

    @Test
    fun `운행 없이도 확정할 수 있다`() = runTest {
        service.confirm(r, null, r.fields)

        assertNull(store.expenses.single().tripId)
    }

    @Test
    fun `음성으로 받은 주유 금액·리터를 운행에 연결해 저장한다`() = runTest {
        val r = service.saveFuel("t-1", VoiceFuel(68_000, 40.0), ExpenseSource.VOICE, kst(9, 20))

        assertTrue(r is ConfirmResult.Saved)
        val e = store.expenses.single()
        assertEquals(ExpenseSource.VOICE, e.source)
        assertEquals(40.0, e.liters!!, 0.0)
        assertNull(e.receiptSha256)
    }

    @Test
    fun `주유 금액이 없거나 단가 검산이 틀리면 저장하지 않는다`() = runTest {
        assertTrue(service.saveFuel(null, VoiceFuel(null, 40.0), ExpenseSource.TAP, 0) is ConfirmResult.Rejected)
        assertTrue(
            service.saveFuel(null, VoiceFuel(90_000, 40.0, 1_700), ExpenseSource.TAP, 0) is ConfirmResult.Rejected,
        )
        assertTrue(store.expenses.isEmpty())
    }

    @Test
    fun `카드 끝자리는 숫자 4개가 아니면 버린다`() {
        assertEquals("5678", ReceiptService.last4("****5678"))
        assertNull(ReceiptService.last4("12"))
        assertNull(ReceiptService.last4(null))
    }
}
