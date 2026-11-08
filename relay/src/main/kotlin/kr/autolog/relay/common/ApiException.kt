package kr.autolog.relay.common

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/** API 오류. 응답은 `{ "code": "...", "message": "..." }` (CONVENTIONS 8장). */
class ApiException(val status: HttpStatus, val code: String, override val message: String) : RuntimeException(message)

data class ErrorBody(val code: String, val message: String)

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(ApiException::class)
    fun handle(e: ApiException): ResponseEntity<ErrorBody> =
        ResponseEntity.status(e.status).body(ErrorBody(e.code, e.message))
}
