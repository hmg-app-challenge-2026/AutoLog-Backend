package kr.autolog.vehicle.session

import kr.autolog.vehicle.core.LiveTrip
import kr.autolog.vehicle.core.SessionState
import kr.autolog.vehicle.core.StopReason
import kr.autolog.vehicle.session.TripNotificationSpec.Action
import kr.autolog.vehicle.session.TripNotificationSpec.Channel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TripNotificationSpecTest {
    private val trip = LiveTrip("t-1", 0, 18_420.5, "판교 테크노밸리 거래처", "u-1")

    @Test
    fun `기록 중에는 거리와 목적지를 조용한 지속 알림으로 보여 준다`() {
        val spec = TripNotificationSpec.from(SessionState.Recording(trip, 12.34, 0))

        assertEquals(Channel.LOW, spec.channel)
        assertEquals("기록 중", spec.title)
        assertEquals("12.3 km · 판교 테크노밸리 거래처", spec.text)
    }

    @Test
    fun `기어 P면 헤즈업으로 묻고 마감 시각까지 카운트다운하며 두 버튼을 준다`() {
        val spec = TripNotificationSpec.from(SessionState.StopCandidate(trip, 23.4, StopReason.GEAR_P, 1_000, 6_000))

        assertEquals(Channel.ALERT, spec.channel)
        assertEquals("기록을 마칠까요?", spec.title)
        assertEquals(6_000L, spec.countdownTo)
        assertEquals(listOf(Action.RESUME, Action.FINALIZE), spec.actions)
    }

    @Test
    fun `운행이 끝나면 탭해서 그 운행의 목적 확인으로 간다`() {
        val spec = TripNotificationSpec.from(SessionState.PendingReview(trip, 23.4, null))

        assertEquals("t-1", spec.reviewTripId)
        assertEquals("23.4 km · 목적을 확인하세요", spec.text)
    }

    @Test
    fun `거리를 모르면 숫자를 지어내지 않는다`() {
        val spec = TripNotificationSpec.from(SessionState.Recording(trip.copy(destinationLabel = null), null, 0))

        assertEquals("거리 확인 필요", spec.text)
        assertNull(spec.countdownTo)
    }
}
