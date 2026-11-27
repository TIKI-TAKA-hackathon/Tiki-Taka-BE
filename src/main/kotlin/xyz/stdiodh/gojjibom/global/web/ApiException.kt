package xyz.stdiodh.gojjibom.global.web

import org.springframework.http.HttpStatus

class ApiException(
    val status: HttpStatus,
    val code: String,
    override val message: String,
) : RuntimeException(message)
