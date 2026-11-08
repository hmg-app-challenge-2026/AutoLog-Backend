package kr.autolog.vehicle.fake

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kr.autolog.vehicle.core.DriverCandidate
import kr.autolog.vehicle.core.DriverSource
import kr.autolog.vehicle.core.PurposeDetail
import kr.autolog.vehicle.core.PurposeEngine
import kr.autolog.vehicle.core.PurposeRuleId
import kr.autolog.vehicle.core.Suggestion
import kr.autolog.vehicle.core.TripFacts
import kr.autolog.vehicle.core.UserBadge
import kr.autolog.vehicle.core.UserCard

/**
 * [PurposeEngine] 가짜. 항상 [suggestion]을 추천하고, 운전자 후보는 [driver]를 준다.
 * 신뢰도 낮은 화면을 보려면 `FakePurposeEngine(suggestion = FakePurposeEngine.UNSURE)`.
 */
class FakePurposeEngine(
    var suggestion: Suggestion = CONFIDENT,
    var driver: DriverCandidate? = SAMPLE_DRIVER,
    var users: List<UserCard> = SAMPLE_USERS,
    var proposePlace: Boolean = true,
) : PurposeEngine {
    override suspend fun suggest(trip: TripFacts): Suggestion = suggestion

    override suspend fun driverCandidate(trip: TripFacts): DriverCandidate? = driver

    override fun orderedUsers(vehicleId: String, now: Long): Flow<List<UserCard>> = flowOf(users)

    override suspend fun shouldProposePlace(trip: TripFacts, detail: PurposeDetail): Boolean = proposePlace

    companion object {
        val CONFIDENT = Suggestion(PurposeDetail.CLIENT_VISIT, 0.97, PurposeRuleId.SCHEDULE, "sch-1")
        val UNSURE = Suggestion(PurposeDetail.NEEDS_CHECK, 0.5, PurposeRuleId.FALLBACK)
        val SAMPLE_DRIVER = DriverCandidate("u-1", "김영업", DriverSource.SCHEDULE, 0.95)
        val SAMPLE_USERS =
            listOf(
                UserCard("u-1", "김영업", "영업1팀", UserBadge.SCHEDULED),
                UserCard("u-2", "이현장", "기술지원팀", UserBadge.RECENT),
                UserCard("u-3", "박관리", "총무팀"),
            )
    }
}
