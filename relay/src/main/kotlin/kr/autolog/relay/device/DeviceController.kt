package kr.autolog.relay.device

import kr.autolog.shared.DeviceRegisterRequest
import kr.autolog.shared.DeviceRegisterResponse
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class DeviceController(private val service: DeviceService) {
    /** 차량 앱 최초 연결: 연결 코드 → 기기 토큰 (F-01, F-40). */
    @PostMapping("/v1/devices")
    fun register(@RequestBody body: DeviceRegisterRequest): DeviceRegisterResponse = service.register(body.code)
}
