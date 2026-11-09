package kr.autolog.vehicle.vehicle

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

/**
 * AAOS 표준 차량 속성 (프론트 스펙 1.1.1 4순위). Pleos SDK 2.3.2에 시동 상태 공개 API가 없어 `CarPropertyManager`로 읽는다.
 * 권한 `android.car.permission.CAR_POWERTRAIN`. 차량 OS가 없거나 속성을 못 읽으면 흐름이 조용히 끝난다.
 */
class CarPropertyGateway(context: Context) {
    private val props: CarPropertyManager? = runCatching {
        Car.createCar(context.applicationContext)?.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
    }.onFailure { Log.w(TAG, "car service unavailable: ${it.message}") }.getOrNull()

    /** IGNITION_STATE 원본 값(0 UNDEFINED, 1 LOCK, 2 OFF, 3 ACC, 4 ON, 5 START). */
    val ignitionState: Flow<Int> = callbackFlow {
        val pm = props
        if (pm == null) {
            close()
            return@callbackFlow
        }
        val callback = object : CarPropertyManager.CarPropertyEventCallback {
            override fun onChangeEvent(value: CarPropertyValue<*>) {
                (value.value as? Int)?.let {
                    Log.d(TAG, "ignition=$it")
                    trySend(it)
                }
            }

            override fun onErrorEvent(propertyId: Int, areaId: Int) {
                Log.w(TAG, "ignition error area=$areaId")
            }
        }
        val ok = runCatching {
            pm.registerCallback(callback, VehiclePropertyIds.IGNITION_STATE, CarPropertyManager.SENSOR_RATE_ONCHANGE)
        }.onFailure { Log.w(TAG, "ignition register failed: ${it.message}") }.getOrDefault(false)
        if (!ok) close()
        awaitClose { runCatching { pm.unregisterCallback(callback) } }
    }

    /** 시동이 꺼지는 순간. */
    val ignitionOff: Flow<Unit> = ignitionState.filter { isOff(it) }.map { }

    companion object {
        private const val TAG = "AutoLog/vehicle"
        private const val LOCK = 1
        private const val OFF = 2
        private const val ACC = 3

        fun isOff(state: Int) = state == LOCK || state == OFF || state == ACC
    }
}
