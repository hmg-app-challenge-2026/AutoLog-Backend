package kr.autolog.vehicle.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** 운행·정차·차량 상태. 전부 suspend/Flow라 Room이 IO 스레드로 보낸다. */
@Dao
interface TripDao {
    @Upsert
    suspend fun upsert(trip: TripEntity)

    @Query("SELECT * FROM trips WHERE tripId = :tripId")
    suspend fun trip(tripId: String): TripEntity?

    /** 끝나지 않은 운행. 앱이 죽었다 살아나면 여기서 복구한다(F-10). 정상이라면 많아야 1건. */
    @Query("SELECT * FROM trips WHERE status = 'RECORDING' ORDER BY startTime DESC LIMIT 1")
    suspend fun findRecording(): TripEntity?

    /** 10초 체크포인트. 다른 열은 건드리지 않는다. */
    @Query("UPDATE trips SET lastOdometerKm = :km, lastSeenAt = :at WHERE tripId = :tripId AND status = 'RECORDING'")
    suspend fun checkpoint(tripId: String, km: Double, at: Long): Int

    @Query(
        "SELECT * FROM trips WHERE startTime >= :from AND startTime < :to AND status != 'DISCARDED' ORDER BY startTime",
    )
    suspend fun tripsBetween(from: Long, to: Long): List<TripEntity>

    @Query(
        "SELECT * FROM trips WHERE startTime >= :from AND startTime < :to " +
            "AND status NOT IN ('RECORDING', 'DISCARDED') ORDER BY startTime DESC",
    )
    fun observeFinishedBetween(from: Long, to: Long): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE status NOT IN ('RECORDING', 'DISCARDED') ORDER BY startTime DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<TripEntity>>

    /** 목적 미확정인 끝난 운행. */
    @Query("SELECT * FROM trips WHERE status IN ('PENDING_REVIEW', 'NEEDS_RECOVERY') ORDER BY startTime DESC")
    fun observePendingReviews(): Flow<List<TripEntity>>

    @Insert
    suspend fun insertStop(stop: StopEntity): Long

    @Query("SELECT * FROM stops WHERE tripId = :tripId ORDER BY startTime")
    suspend fun stops(tripId: String): List<StopEntity>

    @Query("SELECT * FROM vehicle_state WHERE id = 0")
    suspend fun vehicleState(): VehicleStateEntity?

    @Upsert
    suspend fun saveVehicleState(state: VehicleStateEntity)
}
