package kr.autolog.vehicle.session

import kr.autolog.vehicle.core.VehicleMotion
import kr.autolog.vehicle.vehicle.Gear

/**
 * 차의 움직임 판정 (F-13, 프론트 스펙 1.1.1). 순수 함수. 화면의 사용 3단계와 정차 감지가 이 결과를 쓴다.
 *
 * - 기어가 P면 주차.
 * - 기어가 P가 아니면 계기판이 최근 [MOVING_WINDOW_MS] 안에 늘었으면 주행, 아니면 정차.
 * - 기어 신호가 없으면(최후 규칙) 거리 불변 [PARKED_FALLBACK_MS] 이상이거나 관찰 시작 뒤 한 번도 안 움직였으면 주차.
 * - 계기판도 모르면 판정 불가(null) — 화면은 주행으로 본다(보수적).
 */
object DrivingJudge {
    /** 계기판 변화가 이 안에 있으면 주행 중. 기능명세 용어 "정차 상태 = 변화 없음 ≥ 10초". */
    const val MOVING_WINDOW_MS = 10_000L

    /** 기어 신호가 없을 때 이만큼 거리 불변이면 주차로 본다(T_PARKED_FALLBACK_SEC = 60, 프론트 스펙 1.1.1). */
    const val PARKED_FALLBACK_MS = 60_000L

    /**
     * @param gear 마지막 기어. 기어 신호가 아예 없으면 null.
     * @param odometerKnown 계기판을 한 번이라도 읽었는지.
     * @param lastMovedAt 계기판이 마지막으로 늘어난 시각. 관찰 시작 뒤 안 늘었으면 null.
     */
    fun judge(gear: Gear?, odometerKnown: Boolean, lastMovedAt: Long?, now: Long): VehicleMotion? {
        if (gear == Gear.PARK) return VehicleMotion.PARKED
        if (!odometerKnown) return null
        val sinceMove = lastMovedAt?.let { now - it }
        val moving = sinceMove != null && sinceMove < MOVING_WINDOW_MS
        return when {
            moving -> VehicleMotion.MOVING
            gear != null && gear != Gear.UNKNOWN -> VehicleMotion.STOPPED
            sinceMove == null || sinceMove >= PARKED_FALLBACK_MS -> VehicleMotion.PARKED
            else -> VehicleMotion.STOPPED
        }
    }
}
