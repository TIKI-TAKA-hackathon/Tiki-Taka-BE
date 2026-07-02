package xyz.stdiodh.gojjibom.auth

import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import xyz.stdiodh.gojjibom.shared.ApiResponse

@RestController
@RequestMapping("/api/v1")
class AuthController(
    private val authService: AuthService,
) {
    @PostMapping("/auth/otp:request")
    fun requestOtp(
        @Valid @RequestBody request: OtpRequestRequest,
    ): ApiResponse<OtpRequestResponse> = ApiResponse.success(authService.requestOtp(request))

    @PostMapping("/auth/otp:verify")
    fun verifyOtp(
        @Valid @RequestBody request: OtpVerifyRequest,
    ): ApiResponse<OtpVerifyResponse> = ApiResponse.success(authService.verifyOtp(request))
}
