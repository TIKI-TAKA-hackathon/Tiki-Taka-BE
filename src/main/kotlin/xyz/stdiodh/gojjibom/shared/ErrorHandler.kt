package xyz.stdiodh.gojjibom.shared

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

private typealias ErrorResponse = ResponseEntity<ApiResponse<Nothing>>

@RestControllerAdvice
class ErrorHandler {
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
}
