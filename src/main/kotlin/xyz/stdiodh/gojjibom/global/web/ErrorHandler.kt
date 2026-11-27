package xyz.stdiodh.gojjibom.global.web

import org.slf4j.LoggerFactory
import org.springframework.beans.TypeMismatchException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.ErrorResponse as SpringErrorResponse

private typealias ErrorResponse = ResponseEntity<ApiResponse<Nothing>>

@RestControllerAdvice
class ErrorHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(ApiException::class)
    fun handleApiException(exception: ApiException): ErrorResponse =
        ResponseEntity
            .status(exception.status)
            .body(ApiResponse.failure(exception.code, exception.message))

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(exception: MethodArgumentNotValidException): ErrorResponse {
        val message =
            exception.bindingResult
                .fieldErrors
                .firstOrNull()
                ?.let { "${it.field}: ${it.defaultMessage}" }
                ?: "Invalid request"

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.failure("INVALID_REQUEST", message))
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadableMessage(): ErrorResponse =
        ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.failure("INVALID_REQUEST", "Invalid request body"))

    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun handleMissingParameter(exception: MissingServletRequestParameterException): ErrorResponse {
        val message = "Missing request parameter: ${exception.parameterName}"
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.failure("INVALID_REQUEST", message))
    }

    // Spring MVC exceptions that carry their own status (404, 405, 400 type mismatch, ...)
    // are rethrown so the default resolvers keep that status.
    @ExceptionHandler(Exception::class)
    fun handleUnexpected(exception: Exception): ErrorResponse {
        if (exception is SpringErrorResponse || exception is TypeMismatchException) {
            throw exception
        }
        log.error("Unhandled exception", exception)
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.failure("INTERNAL_ERROR", "Internal server error"))
    }
}
