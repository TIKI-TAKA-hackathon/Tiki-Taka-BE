package xyz.stdiodh.gojjibom.notification

import org.springframework.http.HttpStatus
import xyz.stdiodh.gojjibom.shared.ApiException

object NotificationErrors {
    fun forbidden(
        code: String,
        message: String,
    ) = ApiException(HttpStatus.FORBIDDEN, code, message)

    fun notFound(
        code: String,
        message: String,
    ) = ApiException(HttpStatus.NOT_FOUND, code, message)

    fun badRequest(
        code: String,
        message: String,
    ) = ApiException(HttpStatus.BAD_REQUEST, code, message)
}
