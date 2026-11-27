package xyz.stdiodh.gojjibom.prescription.application

import org.springframework.http.HttpStatus
import xyz.stdiodh.gojjibom.global.web.ApiException

object PrescriptionErrors {
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
}
