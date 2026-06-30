package com.tikitaka.memorycard.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app")
data class AppProperties(
    val frontendBaseUrl: String = "http://localhost:5173",
    val corsAllowedOrigins: List<String> = listOf("http://localhost:5173"),
    val shareTokenSecret: String = "change-me",
) {
    fun frontendBaseUrlWithoutTrailingSlash(): String = frontendBaseUrl.trimEnd('/')
}
