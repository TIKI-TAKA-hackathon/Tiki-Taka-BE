package xyz.stdiodh.gojjibom.media

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import java.net.URI

@ConfigurationProperties(prefix = "aws")
data class AwsStorageProperties(
    val region: String = "ap-northeast-2",
    val accessKeyId: String? = null,
    val secretAccessKey: String? = null,
    val s3: AwsS3Properties = AwsS3Properties(),
)

data class AwsS3Properties(
    val bucket: String = "",
    val endpoint: String? = null,
    val pathStyleAccess: Boolean = false,
)

@ConfigurationProperties(prefix = "app.media")
data class MediaProperties(
    val maxImageBytes: Long = DEFAULT_MAX_IMAGE_BYTES,
    val uploadUrlTtlSeconds: Long = 300,
    val viewUrlTtlSeconds: Long = 300,
)

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(
    AwsStorageProperties::class,
    MediaProperties::class,
)
class MediaConfig {
    @Bean
    fun awsCredentialsProvider(properties: AwsStorageProperties): AwsCredentialsProvider {
        val accessKey = properties.accessKeyId?.takeIf { it.isNotBlank() }
        val secretKey = properties.secretAccessKey?.takeIf { it.isNotBlank() }
        if (accessKey != null && secretKey != null) {
            return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey))
        }

        return DefaultCredentialsProvider.builder().build()
    }

    @Bean
    fun s3Client(
        properties: AwsStorageProperties,
        credentialsProvider: AwsCredentialsProvider,
    ): S3Client {
        val builder =
            S3Client
                .builder()
                .region(Region.of(properties.region))
                .credentialsProvider(credentialsProvider)
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .serviceConfiguration(s3Configuration(properties))

        properties.s3.endpoint?.takeIf { it.isNotBlank() }?.let {
            builder.endpointOverride(URI.create(it))
        }

        return builder.build()
    }

    @Bean
    fun s3Presigner(
        properties: AwsStorageProperties,
        credentialsProvider: AwsCredentialsProvider,
    ): S3Presigner {
        val builder =
            S3Presigner
                .builder()
                .region(Region.of(properties.region))
                .credentialsProvider(credentialsProvider)
                .serviceConfiguration(s3Configuration(properties))

        properties.s3.endpoint?.takeIf { it.isNotBlank() }?.let {
            builder.endpointOverride(URI.create(it))
        }

        return builder.build()
    }

    private fun s3Configuration(properties: AwsStorageProperties): S3Configuration =
        S3Configuration
            .builder()
            .pathStyleAccessEnabled(properties.s3.pathStyleAccess)
            .build()
}

private const val DEFAULT_MAX_IMAGE_BYTES = 5L * 1024L * 1024L
