package kr.autolog.relay.common

import kr.autolog.shared.AutoLogJson
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.converter.json.KotlinSerializationJsonHttpMessageConverter
import java.time.Clock

@ConfigurationProperties("autolog")
data class RelayProperties(
    val publicBaseUrl: String = "http://localhost",
    val admin: Admin = Admin(),
    val connectCodeTtlHours: Long = 24,
    val qrTokenTtlMinutes: Long = 10,
    val inboxRetentionDays: Long = 30,
) {
    data class Admin(val token: String = "", val orgName: String = "데모 조직")
}

@Configuration
@EnableConfigurationProperties(RelayProperties::class)
class RelayConfig {
    /** 시각은 주입받는다. 테스트에서 고정 시계로 바꾸기 위해서다. */
    @Bean
    fun clock(): Clock = Clock.systemUTC()

    /** shared 의 @Serializable DTO는 차량 앱과 같은 AutoLogJson 설정으로 직렬화한다. */
    @Bean
    fun kotlinSerializationConverter() = KotlinSerializationJsonHttpMessageConverter(AutoLogJson)
}
