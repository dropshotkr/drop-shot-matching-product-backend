package com.dropshot.matching.controller

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException

data class ErrorResponse(val message: String)

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(IllegalArgumentException::class, IllegalStateException::class)
    fun handleBadRequest(error: RuntimeException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse(error.message ?: "요청을 처리할 수 없습니다."))

    @ExceptionHandler(ResponseStatusException::class)
    fun handleStatus(error: ResponseStatusException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(error.statusCode).body(ErrorResponse(error.reason ?: "요청을 처리할 수 없습니다."))
}
