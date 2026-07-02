package xyz.stdiodh.gojjibom.auth

import jakarta.validation.constraints.NotBlank

data class OtpRequestRequest(
    @field:NotBlank
    val phone: String,
)

data class OtpRequestResponse(
    val sent: Boolean,
)

data class OtpVerifyRequest(
    @field:NotBlank
    val phone: String,
    @field:NotBlank
    val code: String,
)

data class OtpVerifyResponse(
    val verified: Boolean,
)
