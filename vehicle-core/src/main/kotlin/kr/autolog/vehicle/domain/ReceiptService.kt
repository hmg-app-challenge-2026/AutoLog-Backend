package kr.autolog.vehicle.domain

import kr.autolog.shared.ReceiptFields
import kr.autolog.shared.ReceiptPayload
import kr.autolog.vehicle.core.ConfirmResult
import kr.autolog.vehicle.core.DomainStore
import kr.autolog.vehicle.core.ExpenseDraft
import kr.autolog.vehicle.core.ExpenseSource
import kr.autolog.vehicle.core.QrResult
import kr.autolog.vehicle.core.ReceiptApi
import kr.autolog.vehicle.core.RelayClient
import kr.autolog.vehicle.core.TripCandidate
import kr.autolog.vehicle.core.VoiceFuel
import java.time.Clock

/**
 * [ReceiptApi] 구현 (F-19, F-30).
 * - QR: 서버에서 1회용 토큰 URL을 받는다(앱 A의 RelayClient). 토큰이 1회용이라 캐시하지 않는다.
 * - 후보: [ReceiptMatcher].
 * - 확정: 검산·중복 확인 → 비용 저장 → inbox ack. 고친 필드는 editedFields로 남긴다.
 */
class ReceiptService(
    private val store: DomainStore,
    private val relay: RelayClient,
    private val clock: Clock = Clock.systemUTC(),
    private val log: (String) -> Unit = {},
) : ReceiptApi {
    override suspend fun issueQr(): QrResult = try {
        relay.issueQrToken()
    } catch (e: Exception) {
        log("QR 발급 실패: ${e.message}")
        QrResult.Failed("서버에 연결하지 못했습니다")
    }

    override suspend fun candidates(receipt: ReceiptPayload): List<TripCandidate> {
        val at = ReceiptMatcher.receiptTime(receipt) ?: return emptyList()
        return try {
            // 오래 이어진 운행도 잡히도록 시작 시각은 넉넉히 읽는다.
            val trips = store.trips(at - ReceiptMatcher.WINDOW_MS - LONG_TRIP_MS, at + ReceiptMatcher.WINDOW_MS)
            ReceiptMatcher.candidates(at, trips, store.places().map { it.toPlace() })
        } catch (e: Exception) {
            log("영수증 후보 조회 실패 receipt=${receipt.receiptId}: ${e.message}")
            emptyList()
        }
    }

    override suspend fun confirm(receipt: ReceiptPayload, tripId: String?, edits: ReceiptFields): ConfirmResult {
        val amount = edits.amountWon ?: return ConfirmResult.Rejected("결제 금액을 입력해 주세요")
        if (amount <= 0) return ConfirmResult.Rejected("결제 금액이 올바르지 않습니다")
        if (ReceiptArithmetic.check(edits) == false) {
            return ConfirmResult.Rejected("금액이 수량 × 단가 − 할인과 맞지 않습니다")
        }
        return try {
            if (store.hasReceipt(receipt.sha256)) return ConfirmResult.Rejected("이미 저장된 영수증입니다")
            val draft =
                ExpenseDraft(
                    tripId = tripId,
                    occurredAt = parseOrNull(edits.datetime) ?: ReceiptMatcher.receiptTime(receipt) ?: clock.millis(),
                    amountWon = amount,
                    liters = edits.liters,
                    unitPrice = edits.unitPrice,
                    discountWon = edits.discountWon ?: 0,
                    fuelType = edits.fuelType,
                    merchant = edits.merchant,
                    cardLast4 = last4(edits.cardLast4),
                    receiptId = receipt.receiptId,
                    receiptSha256 = receipt.sha256,
                    thumbUrl = receipt.thumbUrl,
                    ocrConfidence = receipt.confidence,
                    editedFields = editedFields(receipt.fields, edits),
                )
            val id = store.saveExpense(draft)
            // ack가 실패해도 비용은 저장됐다. InboxSyncWorker가 다음에 다시 ack한다.
            runCatching { store.ackReceipt(receipt.receiptId) }.onFailure { log("영수증 ack 실패 ${receipt.receiptId}") }
            ConfirmResult.Saved(id)
        } catch (e: Exception) {
            log("영수증 저장 실패 receipt=${receipt.receiptId}: ${e.message}")
            ConfirmResult.Rejected("저장하지 못했습니다")
        }
    }

    override suspend fun saveFuel(tripId: String?, fuel: VoiceFuel, source: ExpenseSource, at: Long): ConfirmResult {
        if (source == ExpenseSource.OCR) return ConfirmResult.Rejected("영수증은 확인 화면에서 저장합니다")
        val amount = fuel.amountWon ?: return ConfirmResult.Rejected("결제 금액을 입력해 주세요")
        if (amount <= 0) return ConfirmResult.Rejected("결제 금액이 올바르지 않습니다")
        if (fuel.liters != null && fuel.liters <= 0) return ConfirmResult.Rejected("주유량이 올바르지 않습니다")
        val fields = ReceiptFields(null, null, null, fuel.liters, fuel.unitPrice, 0, amount, null)
        if (ReceiptArithmetic.check(fields) == false) return ConfirmResult.Rejected("금액이 수량 × 단가와 맞지 않습니다")
        return try {
            val id =
                store.saveExpense(
                    ExpenseDraft(
                        tripId = tripId,
                        occurredAt = at,
                        amountWon = amount,
                        liters = fuel.liters,
                        unitPrice = fuel.unitPrice,
                        discountWon = 0,
                        fuelType = null,
                        merchant = null,
                        cardLast4 = null,
                        source = source,
                    ),
                )
            ConfirmResult.Saved(id)
        } catch (e: Exception) {
            log("주유 기록 저장 실패: ${e.message}")
            ConfirmResult.Rejected("저장하지 못했습니다")
        }
    }

    private fun parseOrNull(iso: String?): Long? =
        iso?.let { runCatching { java.time.OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }

    companion object {
        const val LONG_TRIP_MS = 12 * 60 * 60 * 1000L

        /** 숫자만 남겨 끝 4자리. 4자리가 안 되면 null — 카드번호 전체가 저장되지 않게 한다. */
        fun last4(raw: String?): String? = raw?.filter(Char::isDigit)?.takeLast(4)?.takeIf { it.length == 4 }

        fun editedFields(ocr: ReceiptFields, edits: ReceiptFields): Set<String> = buildSet {
            if (ocr.merchant != edits.merchant) add("merchant")
            if (ocr.datetime != edits.datetime) add("datetime")
            if (ocr.fuelType != edits.fuelType) add("fuelType")
            if (ocr.liters != edits.liters) add("liters")
            if (ocr.unitPrice != edits.unitPrice) add("unitPrice")
            if (ocr.discountWon != edits.discountWon) add("discountWon")
            if (ocr.amountWon != edits.amountWon) add("amountWon")
            if (ocr.cardLast4 != edits.cardLast4) add("cardLast4")
        }
    }
}
