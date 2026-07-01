package xyz.stdiodh.gojjibom.shared

data class ApiResponse<T>(
    val data: T?,
    val error: ApiError?,
) {
    companion object {
        fun <T> success(data: T): ApiResponse<T> = ApiResponse(data = data, error = null)

        fun failure(
            code: String,
            message: String,
        ): ApiResponse<Nothing> = ApiResponse(data = null, error = ApiError(code = code, message = message))
    }
}

data class ApiError(
    val code: String,
    val message: String,
)
