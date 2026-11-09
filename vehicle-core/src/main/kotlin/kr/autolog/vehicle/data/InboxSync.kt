package kr.autolog.vehicle.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kr.autolog.shared.AutoLogJson
import kr.autolog.shared.InboxItem
import kr.autolog.shared.PlacesPayload
import kr.autolog.shared.ReceiptPayload
import kr.autolog.shared.RecipientsPayload
import kr.autolog.shared.SchedulesPayload
import kr.autolog.shared.UsersPayload
import kr.autolog.vehicle.core.Origin
import kr.autolog.vehicle.core.QrResult
import kr.autolog.vehicle.core.RelayClient
import kr.autolog.vehicle.core.SyncResult
import java.time.Clock
import java.time.OffsetDateTime

/**
 * 서버 우체통 → Room, 단방향 (F-11, 기술명세 3.2). 앱 B가 쓰는 [RelayClient]도 구현한다(QR 발급·지금 동기화).
 *
 * - USERS·PLACES·SCHEDULES·RECIPIENTS: SERVER 분만 통째로 교체(LOCAL 보존) → 바로 ack
 * - RECEIPT: 확인 대기 목록에 넣고 **사용자가 확정할 때** ack(RoomDomainStore.ackReceipt). 확정했지만 ack가 실패한 것은 여기서 다시 보낸다
 * - 이미 반영한 편지(inbox_seen)는 다시 반영하지 않고 ack만 한다
 * - 서버가 꺼져 있으면 조용히 실패를 돌려줄 뿐, 로컬 기능을 막지 않는다
 */
class InboxSync(
    private val device: DeviceRepository,
    private val refs: ReferenceDao,
    private val expenses: ExpenseDao,
    private val inbox: InboxDao,
    private val clock: Clock = Clock.systemUTC(),
    private val log: (String) -> Unit = {},
) : RelayClient {
    private val mutex = Mutex()

    override suspend fun issueQrToken(): QrResult = device.issueQr()

    override suspend fun syncNow(): SyncResult = mutex.withLock {
        val items = when (val r = device.call { url, token -> inbox(url, token) }) {
            is RelayResult.Ok -> r.value
            is RelayResult.Failure -> return@withLock SyncResult.Failed(r.message)
        }
        var applied = 0
        for (item in items) {
            val first = !inbox.isSeen(item.id)
            if (first || item.payload is ReceiptPayload) {
                runCatching {
                    apply(item)
                }.onFailure { log("inbox ${item.id} ${item.kind} apply failed: ${it.message}") }
                    .onSuccess { if (first) applied++ }
            }
            if (item.payload !is ReceiptPayload) {
                inbox.markSeen(InboxSeenEntity(item.id, clock.millis()))
                device.call { url, token -> ack(url, token, item.id) }
            }
        }
        retryReceiptAcks()
        device.markSynced()
        log("inbox synced: ${items.size} items, $applied applied")
        SyncResult.Ok(items.size)
    }

    private suspend fun apply(item: InboxItem) {
        when (val p = item.payload) {
            is ReceiptPayload -> {
                val existing = expenses.receipt(p.receiptId)
                if (existing?.confirmedAt != null) return // 이미 확정 — ack만 남았다
                // 서버는 inbox를 가져갈 때마다 썸네일 URL을 새로 서명한다 → payload를 최신으로 바꾼다
                expenses.upsertReceipt(
                    ReceiptInboxEntity(
                        receiptId = p.receiptId,
                        payloadJson = AutoLogJson.encodeToString(ReceiptPayload.serializer(), p),
                        receivedAt = existing?.receivedAt ?: clock.millis(),
                        inboxId = item.id,
                    ),
                )
            }
            is UsersPayload -> refs.replaceServerUsers(
                p.users.map {
                    UserEntity(it.userId, it.name, it.dept, it.email, Origin.SERVER)
                },
            )
            is PlacesPayload -> refs.replaceServerPlaces(
                p.places.map {
                    PlaceEntity(
                        it.placeId,
                        it.label,
                        it.kind,
                        it.lat,
                        it.lng,
                        it.radiusM,
                        it.defaultPurpose,
                        Origin.SERVER,
                    )
                },
            )
            is SchedulesPayload -> refs.replaceSchedules(
                p.schedules.mapNotNull { s ->
                    val from = parseTime(s.startsAt) ?: return@mapNotNull null
                    val to = parseTime(s.endsAt) ?: return@mapNotNull null
                    ScheduleEntity(s.scheduleId, s.vehicleId, s.userId, from, to, s.destPlaceId, s.purposeDetail)
                },
            )
            is RecipientsPayload -> refs.replaceServerRecipients(
                p.recipients.map {
                    RecipientEntity(it.email, it.name, Origin.SERVER)
                },
            )
        }
    }

    /** 확정했지만 서버 ack가 실패한 영수증을 다시 ack한다. 성공하면 지운다. */
    private suspend fun retryReceiptAcks() {
        for (r in expenses.confirmedReceipts()) {
            val id = r.inboxId ?: continue
            if (device.call { url, token -> ack(url, token, id) } is RelayResult.Ok) expenses.deleteReceipt(r.receiptId)
        }
    }

    companion object {
        /** 서버 시각은 ISO 8601 `+09:00` 문자열(CONVENTIONS 6.3). 앱 안에서는 epoch millis. */
        fun parseTime(iso: String): Long? =
            runCatching { OffsetDateTime.parse(iso).toInstant().toEpochMilli() }.getOrNull()
    }
}
