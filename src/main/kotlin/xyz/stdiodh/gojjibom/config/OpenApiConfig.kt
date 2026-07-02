package xyz.stdiodh.gojjibom.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.servers.Server
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig(
    @param:Value("\${app.public-base-url}") private val publicBaseUrl: String,
) {
    @Bean
    fun openApi(): OpenAPI {
        val openApi =
            OpenAPI()
                .info(
                    Info()
                        .title("Gojjibom API")
                        .description("Gojjibom backend API")
                        .version("0.0.1"),
                )

        if (publicBaseUrl.isNotBlank()) {
            openApi.servers(listOf(Server().url(publicBaseUrl)))
        }

        return openApi
    }
}
