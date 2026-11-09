package kr.autolog.vehicle.session

import kr.autolog.vehicle.core.SessionState

/**
 * 세션 상태 → 알림 내용 (F-03, F-16, 프론트 스펙 R-7·7.2.1). 순수 함수 — Android Notification은 서비스가 만든다.
 * 주행 중에는 앱 화면이 없으므로 사람이 보는 것은 이 알림뿐이다.
 */
data class TripNotificationSpec(
    val channel: Channel,
    val title: String,
    val text: String,
    /** 카운트다운 목표 시각(epoch millis). null이면 카운트다운 없음. */
    val countdownTo: Long? = null,
    val actions: List<Action> = emptyList(),
    /** 탭하면 이 운행의 목적 확인 화면으로(DeepLink). null이면 앱 첫 화면. */
    val reviewTripId: String? = null,
) {
    /** LOW = 지속 알림(조용히), ALERT = 헤즈업. */
    enum class Channel { LOW, ALERT }

    enum class Action { RESUME, FINALIZE }

    companion object {
        fun from(state: SessionState): TripNotificationSpec = when (state) {
            SessionState.Idle -> TripNotificationSpec(Channel.LOW, "AutoLog", "다음 운행을 기다리는 중")
            is SessionState.Recording ->
                TripNotificationSpec(Channel.LOW, "기록 중", line(km(state.liveKm), state.trip.destinationLabel))
            is SessionState.StopCandidate -> TripNotificationSpec(
                channel = Channel.ALERT,
                title = "기록을 마칠까요?",
                text = line(km(state.liveKm), state.trip.destinationLabel),
                countdownTo = state.deadlineAt,
                actions = listOf(Action.RESUME, Action.FINALIZE),
            )
            is SessionState.PendingReview -> TripNotificationSpec(
                channel = Channel.ALERT,
                title = "운행 종료",
                text = line(km(state.distanceKm), "목적을 확인하세요"),
                reviewTripId = state.trip.tripId,
            )
            is SessionState.NeedsRecovery -> TripNotificationSpec(
                channel = Channel.ALERT,
                title = "기록하지 못한 운행이 있어요",
                text = "앱을 열어 기록할지 정해 주세요",
            )
        }

        /** 거리 표시: 소수 1자리, 모르면 "거리 확인 필요"(추정하지 않는다). */
        fun km(value: Double?): String = value?.let { "%.1f km".format(it) } ?: "거리 확인 필요"

        private fun line(vararg parts: String?) = parts.filterNot { it.isNullOrBlank() }.joinToString(" · ")
    }
}
