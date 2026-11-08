package kr.autolog.vehicle.fake

import kotlinx.coroutines.delay
import kr.autolog.vehicle.core.PurposeDetail
import kr.autolog.vehicle.core.VoiceApi
import kr.autolog.vehicle.core.VoiceFuel
import kr.autolog.vehicle.core.VoicePurpose
import kr.autolog.vehicle.core.VoiceResult

/** [VoiceApi] 가짜. [listenMs]만큼 듣는 척한 뒤 정해 둔 결과를 준다. */
class FakeVoiceApi(
    var purpose: VoiceResult<VoicePurpose> = VoiceResult.Recognized("거래처 미팅", VoicePurpose(PurposeDetail.CLIENT_VISIT)),
    var fuel: VoiceResult<VoiceFuel> = VoiceResult.Recognized("휘발유 칠만 원 사십 리터", VoiceFuel(70_000, 40.0)),
    private val listenMs: Long = 1_500,
) : VoiceApi {
    override suspend fun listenPurpose(): VoiceResult<VoicePurpose> {
        delay(listenMs)
        return purpose
    }

    override suspend fun listenFuel(): VoiceResult<VoiceFuel> {
        delay(listenMs)
        return fuel
    }
}
