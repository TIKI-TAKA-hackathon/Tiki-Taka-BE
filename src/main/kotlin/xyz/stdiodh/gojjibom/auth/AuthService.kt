package xyz.stdiodh.gojjibom.auth

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import xyz.stdiodh.gojjibom.shared.ApiException

@Service
class AuthService {
    /**
     * Demo stub: no SMS is sent and nothing is persisted; the request is only echoed as "sent".
     */
    fun requestOtp(request: OtpRequestRequest): OtpRequestResponse {
        // The phone is validated as non-blank by the DTO; a real implementation would send an SMS here.
        require(request.phone.isNotBlank())
        return OtpRequestResponse(sent = true)
    }

    /**
     * Demo stub: any 6-digit numeric code is accepted; anything else is rejected.
     */
    fun verifyOtp(request: OtpVerifyRequest): OtpVerifyResponse {
        if (!OTP_CODE_PATTERN.matches(request.code)) {
            throw ApiException(HttpStatus.BAD_REQUEST, "OTP_INVALID", "Verification code must be 6 digits")
        }
        return OtpVerifyResponse(verified = true)
    }

    private companion object {
        private val OTP_CODE_PATTERN = Regex("^\\d{6}$")
    }
}
