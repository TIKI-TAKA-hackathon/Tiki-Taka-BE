package com.tikitaka.memorycard.shared

import com.tikitaka.memorycard.config.AppProperties
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Component
class TokenGenerator(
    private val appProperties: AppProperties,
) {
    private val secureRandom = SecureRandom()
    private val encoder = Base64.getUrlEncoder().withoutPadding()

    fun generate(): String {
        val randomBytes = ByteArray(32)
        secureRandom.nextBytes(randomBytes)

        val signature = hmac(randomBytes).copyOfRange(0, 8)
        return encoder.encodeToString(randomBytes + signature)
    }

    private fun hmac(value: ByteArray): ByteArray {
        val key = SecretKeySpec(appProperties.shareTokenSecret.toByteArray(Charsets.UTF_8), "HmacSHA256")
        return Mac.getInstance("HmacSHA256").apply { init(key) }.doFinal(value)
    }
}
