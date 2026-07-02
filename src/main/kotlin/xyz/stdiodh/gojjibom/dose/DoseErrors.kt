package xyz.stdiodh.gojjibom.dose

import org.springframework.http.HttpStatus
import xyz.stdiodh.gojjibom.shared.ApiException

object DoseErrors {
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
}
