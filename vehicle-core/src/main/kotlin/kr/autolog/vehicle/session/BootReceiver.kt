package kr.autolog.vehicle.session

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kr.autolog.vehicle.core.AutoLog

/**
 * 부팅(=시동) 뒤 앱을 열지 않아도 기록을 준비한다 (F-14). AutoLog를 조립하면 세션 복구·차량 신호 구독·기록 서비스가 시작되고,
 * 차가 움직이면 운행이 자동으로 시작된다. specialUse 포그라운드 서비스는 BOOT_COMPLETED에서 시작이 허용된다.
 * [검증 필요] Pleos가 서드파티 앱에 BOOT_COMPLETED를 보내는지, 런타임 권한이 이미 허용돼 있어야 좌표가 붙는다.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        Log.i("AutoLog/session", "boot: ${intent.action}")
        runCatching {
            AutoLog.create(context)
        }.onFailure { Log.w("AutoLog/session", "boot start failed: ${it.message}") }
    }
}
