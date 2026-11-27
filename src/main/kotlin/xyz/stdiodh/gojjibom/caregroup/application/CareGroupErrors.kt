package xyz.stdiodh.gojjibom.caregroup.application

import org.springframework.http.HttpStatus
import xyz.stdiodh.gojjibom.global.web.ApiException

object CareGroupErrors {
    fun badRequest(
        code: String,
        message: String,
    ) = ApiException(HttpStatus.BAD_REQUEST, code, message)

    fun forbidden(
        code: String,
        message: String,
    ) = ApiException(HttpStatus.FORBIDDEN, code, message)

    fun notFound(
        code: String,
        message: String,
    ) = ApiException(HttpStatus.NOT_FOUND, code, message)

    fun conflict(
        code: String,
        message: String,
    ) = ApiException(HttpStatus.CONFLICT, code, message)

    fun gone(
        code: String,
        message: String,
    ) = ApiException(HttpStatus.GONE, code, message)
}
