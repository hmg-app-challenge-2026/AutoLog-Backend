package kr.autolog.relay

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class RelayApplication

fun main(args: Array<String>) {
    runApplication<RelayApplication>(*args)
}
