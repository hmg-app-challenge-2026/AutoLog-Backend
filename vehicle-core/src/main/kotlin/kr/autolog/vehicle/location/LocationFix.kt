package kr.autolog.vehicle.location

/**
 * Fused Location 좌표 한 건 (F-07). SDK의 LocationData를 옮긴 것.
 *
 * SDK에는 accuracy(m)가 없고 confidence만 있다(FusedLocation 1.0.4 AAR 확인).
 * confidence의 범위·의미는 에뮬레이터에서 확인한다. [검증 필요]
 */
data class LocationFix(
    val lat: Double,
    val lng: Double,
    val headingDeg: Float,
    val confidence: Float,
    val timestampMs: Long,
)

/**
 * 출발·도착 스냅샷에 쓸 좌표를 고른다. 순수 로직이라 JVM 테스트가 된다.
 *
 * - 최신 fix가 [maxAgeMs] 이내면 그것을 쓴다.
 * - 너무 오래됐으면 쓰지 않는다(null). 오래된 좌표로 장소를 잘못 매칭하는 것보다 폴백이 낫다.
 */
object FixSelector {
    const val DEFAULT_MAX_AGE_MS = 30_000L

    fun freshOrNull(latest: LocationFix?, nowMs: Long, maxAgeMs: Long = DEFAULT_MAX_AGE_MS): LocationFix? =
        latest?.takeIf { nowMs - it.timestampMs in 0..maxAgeMs }
}
