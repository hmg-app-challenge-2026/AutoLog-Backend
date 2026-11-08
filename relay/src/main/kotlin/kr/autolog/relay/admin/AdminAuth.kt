package kr.autolog.relay.admin

import kr.autolog.relay.common.ApiException
import kr.autolog.relay.common.RelayProperties
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.util.UUID

/**
 * 관리자 인증. JWT(명세 4.6) 도입 전까지 `X-Admin-Token` 헤더 = 환경변수 ADMIN_TOKEN. 값이 비어 있으면 전부 403.
 * 통과하면 조직 id(현재 단일 조직)를 돌려준다.
 */
@Component
class AdminAuth(private val vehicles: AdminVehicleRepository, private val props: RelayProperties) {
    fun authorize(token: String?): UUID {
        val expected = props.admin.token
        val ok =
            expected.isNotEmpty() && token != null && MessageDigest.isEqual(expected.toByteArray(), token.toByteArray())
        if (!ok) throw ApiException(HttpStatus.FORBIDDEN, "ADMIN_FORBIDDEN", "관리자 토큰이 없거나 틀렸습니다")
        return vehicles.ensureOrg(props.admin.orgName)
    }
}
