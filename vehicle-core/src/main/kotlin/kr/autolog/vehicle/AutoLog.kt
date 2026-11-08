package kr.autolog.vehicle

import kotlinx.coroutines.flow.StateFlow

/**
 * 화면(app)과 차량 로직(vehicle-core)의 경계.
 * app 은 이 인터페이스만 사용하고, 내부 패키지(session/data/...)에 직접 접근하지 않는다.
 * 함수가 필요하면 여기에 추가하고 화면 담당과 합의한다.
 */
interface AutoLog {
    /** 현재 운행 상태. 화면은 collect 해서 그린다. */
    val tripState: StateFlow<TripState>

    /** 정차 상태 여부(F-13). false 면 화면은 입력을 막는다. */
    val isParked: StateFlow<Boolean>

    fun selectDriver(userId: String?)                       // F-15
    suspend fun startTrip()                                 // F-02
    suspend fun endTrip()                                   // F-04
    suspend fun confirmPurpose(tripId: String, purpose: Purpose, note: String? = null)  // F-08
}

/** 기획서 3.1 운행 세션 상태. */
sealed interface TripState {
    data object Idle : TripState
    data class Recording(
        val tripId: String,
        val liveKm: Double?,               // null = Odometer 조회 불가
        val destinationLabel: String?,
        val suggestedPurpose: Purpose?,
    ) : TripState
    data class StopCandidate(val tripId: String, val liveKm: Double?) : TripState
    data class PendingReview(val tripId: String, val distanceKm: Double?, val suggestedPurpose: Purpose?) : TripState
    data class NeedsRecovery(val tripId: String, val reason: String) : TripState
}

/** 국세청 운행기록부 구분 + 세부 목적 (기획서 3.2). */
enum class Purpose(val category: Category) {
    CLIENT_VISIT(Category.BUSINESS), FIELD_WORK(Category.BUSINESS), BUSINESS_TRIP(Category.BUSINESS),
    MEETING(Category.BUSINESS), BRANCH_MOVE(Category.BUSINESS),
    COMMUTE_IN(Category.COMMUTE), COMMUTE_OUT(Category.COMMUTE),
    PERSONAL(Category.NON_BUSINESS),
    UNDETERMINED(Category.UNDETERMINED);

    enum class Category { BUSINESS, COMMUTE, NON_BUSINESS, UNDETERMINED }
}
