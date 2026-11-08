package kr.autolog.relay.common

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class PingController {
    @GetMapping("/v1/ping")
    fun ping() = mapOf("service" to "autolog-relay", "ok" to true)
}
