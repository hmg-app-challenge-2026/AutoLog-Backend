// Contracts.kt 인터페이스가 주고받는 타입. 프론트도 쓰므로 Android·SDK·Room 타입을 넣지 않는다.
package kr.autolog.vehicle.core

import java.io.File
import java.time.YearMonth

// ===== 앱 A·B 공용 =====

/** 운전자가 어떻게 정해졌는지 (기술명세 2.9 TripEntity.driverSource). */
enum class DriverSource {
    DRIVER_SELECTED,
    SCHEDULE,
    ASSIGNMENT,
    PREVIOUS_TRIP,
    SELF_CLAIMED,
    MANAGER_ASSIGNED,
}

/**
 * 추천 엔진에 넘기는 운행 정보. 앱 A가 TripEntity에서 옮겨 담는다.
 * (B가 A의 Room 엔티티에 직접 의존하지 않기 위한 경계 타입.)
 */
data class TripFacts(
    val tripId: String,
    val vehicleId: String,
    /** 운행 시작(epoch millis, UTC). */
    val startTime: Long,
    val endTime: Long? = null,
    val startLat: Double? = null,
    val startLng: Double? = null,
    val endLat: Double? = null,
    val endLng: Double? = null,
    /** 내비 목적지 라벨. 없으면 null. */
    val destinationLabel: String? = null,
    val driverUserId: String? = null,
)

// ===== 앱 B 제공: 맥락 (F-06, F-07) =====

/** 내비 목적지. */
data class DestinationInfo(
    val label: String,
    val address: String?,
    val lat: Double,
    val lng: Double,
    val poiId: String? = null,
)

/** 내비 도착 알림. */
data class ArrivalInfo(val label: String?, val address: String?, val at: Long)

/**
 * 좌표 한 건. SDK에는 accuracy(m)가 없고 confidence만 있다(FusedLocation 1.0.4).
 * 로그에는 소수 3자리까지만 남긴다(CONVENTIONS 7.6).
 */
data class LocationFix(
    val lat: Double,
    val lng: Double,
    val headingDeg: Float,
    val confidence: Float,
    val timestampMs: Long,
)

// ===== 앱 B 제공: 목적·운전자 (F-08, F-15, F-20) =====

/** 국세청 운행기록부 구분 (기획서 3.2). */
enum class PurposeCategory(val labelKo: String) {
    BUSINESS("일반 업무용"),
    COMMUTE("출퇴근용"),
    NON_BUSINESS("비업무용"),
    UNDETERMINED("미확정"),
}

/** 세부 목적. 저장할 때는 name 문자열로 쓴다(TripEntity.purposeDetail). */
enum class PurposeDetail(val category: PurposeCategory, val labelKo: String) {
    CLIENT_VISIT(PurposeCategory.BUSINESS, "거래처 방문"),
    FIELD_WORK(PurposeCategory.BUSINESS, "외근"),
    BUSINESS_TRIP(PurposeCategory.BUSINESS, "출장"),
    MEETING(PurposeCategory.BUSINESS, "회의"),
    BRANCH_MOVE(PurposeCategory.BUSINESS, "지점 이동"),
    COMMUTE_IN(PurposeCategory.COMMUTE, "출근"),
    COMMUTE_OUT(PurposeCategory.COMMUTE, "퇴근"),
    PERSONAL(PurposeCategory.NON_BUSINESS, "개인 이동"),
    NEEDS_CHECK(PurposeCategory.UNDETERMINED, "확인 필요"),
    ;

    companion object {
        /** 서버·Room 문자열을 안전하게 읽는다. 모르는 값이면 null. */
        fun fromName(name: String?): PurposeDetail? = entries.firstOrNull { it.name == name }
    }
}

/** 어느 규칙이 추천했는지. 출처를 남기기 위한 값(CONVENTIONS 7.1). */
enum class PurposeRuleId {
    SCHEDULE,
    COMMUTE_IN,
    COMMUTE_OUT,
    CLIENT,
    BRANCH,
    PAST_CONFIRMED,
    WEEKEND_UNKNOWN,
    FALLBACK,
}

/** 목적 추천 결과. 화면은 [detail]을 큰 버튼으로 보여 주고 사용자가 1탭으로 확정한다. */
data class Suggestion(
    val detail: PurposeDetail,
    val confidence: Double,
    val rule: PurposeRuleId,
    /** 배차에서 온 경우 그 배차 id. */
    val scheduleId: String? = null,
) {
    val category: PurposeCategory get() = detail.category

    /** 확신이 낮으면 화면이 추천 버튼 대신 선택지를 먼저 보여 준다(기획서 3.2 "확신도가 낮으면 반드시 묻는다"). */
    val isConfident: Boolean get() = confidence >= CONFIDENT_THRESHOLD

    companion object {
        const val CONFIDENT_THRESHOLD = 0.8
    }
}

/** 운전자 후보. */
data class DriverCandidate(val userId: String, val name: String, val source: DriverSource, val confidence: Double)

/** 운전자 선택 화면의 카드 한 장. */
data class UserCard(
    val userId: String,
    val name: String,
    val dept: String?,
    /** 위로 올라온 이유. 화면이 "배차" "최근" 배지로 보여 준다. 이름순이면 null. */
    val badge: UserBadge? = null,
)

enum class UserBadge { SCHEDULED, RECENT }

// ===== 앱 B 제공: 음성 (F-09, F-18) =====

/** 음성 인식 결과. */
sealed interface VoiceResult<out T> {
    /** 알아듣고 값까지 뽑았다. [text]는 인식한 원문(화면에 보여 준다). */
    data class Recognized<T>(val text: String, val value: T) : VoiceResult<T>

    /** 말은 받았지만 값을 못 뽑았다. 화면은 탭 입력으로 넘어간다. */
    data class NotUnderstood(val text: String) : VoiceResult<Nothing>

    /** 마이크·STT 실패나 사용자가 말하지 않음. */
    data class Failed(val reason: String) : VoiceResult<Nothing>
}

data class VoicePurpose(val detail: PurposeDetail, val note: String? = null)

data class VoiceFuel(val amountWon: Int?, val liters: Double?, val unitPrice: Int? = null)

// ===== 앱 B 제공: 영수증 (F-19, F-30) =====

sealed interface QrResult {
    /** [url]을 QR로 그린다. [expiresAt](epoch millis)이 지나면 다시 발급한다. */
    data class Issued(val url: String, val expiresAt: Long) : QrResult

    /** 네트워크·서버 오류. 화면은 "연결을 확인해 주세요". */
    data class Failed(val reason: String) : QrResult
}

/** 영수증을 붙일 운행 후보. */
data class TripCandidate(
    val tripId: String,
    val startTime: Long,
    val endTime: Long?,
    /** 화면 표시용 한 줄. 예: "09:12 본사 → 판교 거래처". */
    val label: String,
    /** 주유소에서 정차한 운행이면 true. */
    val hasFuelStop: Boolean,
)

sealed interface ConfirmResult {
    data class Saved(val expenseId: String) : ConfirmResult

    /** 검산 실패 등으로 저장하지 않았다(F-19 "검산 실패 시 확정 비활성"). */
    data class Rejected(val reason: String) : ConfirmResult
}

// ===== 앱 B 제공: 메일 (F-22, F-24) =====

/** 앱 A가 만든 운행기록부 파일. */
data class ReportFile(val month: YearMonth, val file: File)

/** SMTP 설정. [toString]에 비밀번호가 찍히지 않게 가린다(CONVENTIONS 7.6). */
data class SmtpConfig(
    val host: String,
    val port: Int = 587,
    val username: String,
    val password: String,
    val fromName: String? = null,
    val startTls: Boolean = true,
) {
    override fun toString(): String =
        "SmtpConfig(host=$host, port=$port, username=$username, password=***, fromName=$fromName, startTls=$startTls)"
}

sealed interface MailResult {
    /** 보냈다(send) 또는 저장했다(saveSmtp). */
    data object Ok : MailResult

    data class Failed(val error: MailError, val message: String) : MailResult
}

enum class MailError { NOT_CONFIGURED, AUTH, NETWORK, INVALID_RECIPIENT, UNKNOWN }
