package com.tikitaka.memorycard.shared

data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val message: String? = null,
)
