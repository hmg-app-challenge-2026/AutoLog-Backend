package kr.autolog.vehicle.session

import kr.autolog.vehicle.core.DestinationInfo
import kr.autolog.vehicle.core.LiveTrip
import kr.autolog.vehicle.core.TripFacts
import kr.autolog.vehicle.data.PlaceSource
import kr.autolog.vehicle.data.TripEntity

/** Room 운행 → 화면용 진행 중 운행. */
internal fun TripEntity.toLive() = LiveTrip(
    tripId = tripId,
    startTime = startTime,
    startOdometerKm = startOdometerKm,
    destinationLabel = destinationLabel,
    driverUserId = userId,
    originLabel = originLabel,
)

/** Room 운행 → 앱 B 추천 엔진 입력(경계 타입). */
internal fun TripEntity.toFacts() = TripFacts(
    tripId = tripId,
    vehicleId = vehicleId,
    startTime = startTime,
    endTime = endTime,
    startLat = originLat,
    startLng = originLng,
    endLat = destinationLat,
    endLng = destinationLng,
    destinationLabel = destinationLabel,
    driverUserId = userId,
)

/** 내비 목적지를 운행에 붙인다(F-06). 마지막 값이 남는다. */
internal fun TripEntity.withDestination(d: DestinationInfo) = copy(
    destinationLabel = d.label,
    destinationLat = d.lat,
    destinationLng = d.lng,
    destinationSource = PlaceSource.NAVI_HELPER,
)
