package kr.autolog.vehicle.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * 차 안 원본 저장소. 스키마는 `vehicle-core/schemas/`에 내보내 커밋한다.
 * 버전을 올릴 때는 Migration을 쓴다 — 운행 기록은 원본이라 destructive migration을 쓰지 않는다.
 */
@Database(
    entities = [
        TripEntity::class,
        StopEntity::class,
        VehicleStateEntity::class,
        UserEntity::class,
        RecentUserEntity::class,
        PlaceEntity::class,
        ScheduleEntity::class,
        RecipientEntity::class,
        ExpenseEntity::class,
        TripExpenseLinkEntity::class,
        ReceiptInboxEntity::class,
        InboxSeenEntity::class,
        ReportEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AutoLogDb : RoomDatabase() {
    abstract fun tripDao(): TripDao

    companion object {
        const val NAME = "autolog.db"

        fun open(context: Context): AutoLogDb =
            Room.databaseBuilder(context.applicationContext, AutoLogDb::class.java, NAME).build()
    }
}
