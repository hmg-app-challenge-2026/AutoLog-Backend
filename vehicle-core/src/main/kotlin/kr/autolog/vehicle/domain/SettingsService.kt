package kr.autolog.vehicle.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kr.autolog.vehicle.core.DomainStore
import kr.autolog.vehicle.core.Origin
import kr.autolog.vehicle.core.RecipientInfo
import kr.autolog.vehicle.core.RelayClient
import kr.autolog.vehicle.core.SettingsApi
import kr.autolog.vehicle.core.SettingsResult
import kr.autolog.vehicle.core.SyncResult
import kr.autolog.vehicle.core.UserInfo
import java.text.Collator
import java.util.Locale
import java.util.UUID

/** 월말 자동 발송 스위치 저장소. Android 구현은 mail/PrefsAutoSendPreference. */
interface AutoSendPreference {
    fun load(): Boolean

    fun save(enabled: Boolean)
}

/**
 * [SettingsApi] 구현 (F-24). 수신자·직원은 차 안 Room([DomainStore])에 origin=LOCAL로 저장한다.
 * 서버 배포분(origin=SERVER)은 inbox 수신 때 바뀌므로 여기서 지우거나 고치지 않는다.
 */
class SettingsService(
    private val store: DomainStore,
    private val relay: RelayClient,
    private val autoSend: AutoSendPreference,
    private val newId: () -> String = { "local-${UUID.randomUUID()}" },
    private val log: (String) -> Unit = {},
) : SettingsApi {
    private val autoState = MutableStateFlow(autoSend.load())

    override fun recipients(): Flow<List<RecipientInfo>> = store.recipients().map(RecipientBook::merge)

    override suspend fun addRecipient(email: String, name: String?): SettingsResult {
        val normalized = RecipientBook.normalize(email)
        if (!RecipientBook.isValid(normalized)) return SettingsResult.Invalid("이메일 형식이 올바르지 않습니다")
        return guarded("수신자 추가") {
            if (store.recipients().first().any { it.email.equals(normalized, true) && it.origin == Origin.SERVER }) {
                SettingsResult.Invalid("관리자가 이미 배포한 수신자입니다")
            } else {
                store.saveRecipient(RecipientInfo(normalized, name?.trim()?.ifEmpty { null }, Origin.LOCAL))
                SettingsResult.Ok
            }
        }
    }

    override suspend fun removeRecipient(email: String): SettingsResult {
        val normalized = RecipientBook.normalize(email)
        return guarded("수신자 삭제") {
            val matches = store.recipients().first().filter { it.email.equals(normalized, true) }
            when {
                matches.none { it.origin == Origin.LOCAL } && matches.any { it.origin == Origin.SERVER } ->
                    SettingsResult.Invalid("관리자가 배포한 수신자는 지울 수 없습니다")
                matches.isEmpty() -> SettingsResult.Invalid("없는 수신자입니다")
                else -> {
                    store.deleteLocalRecipient(matches.first { it.origin == Origin.LOCAL }.email)
                    SettingsResult.Ok
                }
            }
        }
    }

    override fun users(): Flow<List<UserInfo>> = store.users().map { list -> list.sortedWith(NAME_ORDER) }

    override suspend fun addUser(name: String, dept: String?, email: String?): SettingsResult {
        val n = name.trim()
        if (n.isEmpty()) return SettingsResult.Invalid("이름을 입력해 주세요")
        val mail = email?.let(RecipientBook::normalize)?.ifEmpty { null }
        if (mail != null && !RecipientBook.isValid(mail)) return SettingsResult.Invalid("이메일 형식이 올바르지 않습니다")
        return guarded("직원 추가") {
            store.saveUser(UserInfo(newId(), n, dept?.trim()?.ifEmpty { null }, mail, Origin.LOCAL))
            SettingsResult.Ok
        }
    }

    override val autoMonthlySend: StateFlow<Boolean> = autoState.asStateFlow()

    override suspend fun setAutoMonthlySend(enabled: Boolean) {
        autoSend.save(enabled)
        autoState.value = enabled
    }

    override suspend fun syncNow(): SyncResult = try {
        relay.syncNow()
    } catch (e: Exception) {
        log("동기화 실패: ${e.message}")
        SyncResult.Failed("서버에 연결하지 못했습니다")
    }

    private suspend fun guarded(what: String, block: suspend () -> SettingsResult): SettingsResult = try {
        block()
    } catch (e: Exception) {
        log("$what 실패: ${e.message}")
        SettingsResult.Invalid("저장하지 못했습니다")
    }

    private companion object {
        val NAME_ORDER: Comparator<UserInfo> =
            Collator.getInstance(Locale.KOREAN).let { c -> Comparator { a, b -> c.compare(a.name, b.name) } }
    }
}

/** 수신자 목록 규칙 (F-24). 순수 함수. */
object RecipientBook {
    private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    fun normalize(email: String): String = email.trim().lowercase(Locale.ROOT)

    fun isValid(email: String): Boolean = EMAIL.matches(email)

    /** 같은 이메일은 서버 배포분 하나만 남기고, 서버 배포분 → 직접 입력 순, 각각 이메일순. */
    fun merge(list: List<RecipientInfo>): List<RecipientInfo> = list
        .groupBy { normalize(it.email) }
        .map { (_, same) -> same.firstOrNull { it.origin == Origin.SERVER } ?: same.first() }
        .sortedWith(compareBy<RecipientInfo> { it.origin != Origin.SERVER }.thenBy { normalize(it.email) })
}
