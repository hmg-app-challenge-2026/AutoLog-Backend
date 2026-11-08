package kr.autolog.relay.device

import kr.autolog.relay.common.ApiException
import kr.autolog.relay.common.Tokens
import kr.autolog.shared.DeviceRegisterResponse
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/**
 * 기기 등록과 기기 토큰 인증 (F-40).
 * 연결 코드는 1회·24시간. 토큰 원문은 응답으로 한 번만 주고, DB에는 해시만 남긴다.
 */
@Service
class DeviceService(private val repo: DeviceRepository, private val clock: Clock) {
    @Transactional
    fun register(code: String): DeviceRegisterResponse {
        val now = clock.instant()
        val connect = repo.findConnectCode(code.trim().uppercase())
        if (connect == null || connect.usedAt != null || !connect.expiresAt.isAfter(now)) {
            throw ApiException(HttpStatus.BAD_REQUEST, "CONNECT_CODE_INVALID", "연결 코드가 없거나 만료됐거나 이미 사용됐습니다")
        }
        if (!repo.markCodeUsed(connect.code, now)) {
            throw ApiException(HttpStatus.BAD_REQUEST, "CONNECT_CODE_INVALID", "연결 코드가 이미 사용됐습니다")
        }
        val token = Tokens.newToken()
        val vehicle = repo.saveDeviceToken(connect.vehicleId, Tokens.sha256Hex(token), now)
        return DeviceRegisterResponse(
            deviceToken = token,
            vehicleId = vehicle.vehicleId.toString(),
            orgId = vehicle.orgId.toString(),
        )
    }

    /** `Authorization: Bearer <기기 토큰>` 헤더로 차량을 찾는다. 없으면 401. */
    fun authenticate(authorization: String?): DeviceVehicle {
        val token = authorization?.removePrefix("Bearer ")?.trim()
        if (token.isNullOrEmpty()) throw unauthorized()
        return repo.findByTokenHash(Tokens.sha256Hex(token)) ?: throw unauthorized()
    }

    private fun unauthorized() = ApiException(HttpStatus.UNAUTHORIZED, "DEVICE_UNAUTHORIZED", "기기 토큰이 없거나 회수됐습니다")
}
