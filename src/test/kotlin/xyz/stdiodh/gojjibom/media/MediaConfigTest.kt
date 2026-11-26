package xyz.stdiodh.gojjibom.media

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import java.net.URI
import java.time.Duration

class MediaConfigTest {
    private val config = MediaConfig()
    private val staticCredentials =
        StaticCredentialsProvider.create(AwsBasicCredentials.create("test-access-key", "test-secret-key"))

    @Test
    fun `static credentials are used only when both keys are present`() {
        val provider =
            config.awsCredentialsProvider(
                AwsStorageProperties(accessKeyId = "test-access-key", secretAccessKey = "test-secret-key"),
            )

        assertThat(provider).isInstanceOf(StaticCredentialsProvider::class.java)
        assertThat(provider.resolveCredentials().accessKeyId()).isEqualTo("test-access-key")
        assertThat(provider.resolveCredentials().secretAccessKey()).isEqualTo("test-secret-key")
    }

    @Test
    fun `missing or blank keys fall back to the default credentials chain`() {
        val incomplete =
            listOf(
                AwsStorageProperties(),
                AwsStorageProperties(accessKeyId = "test-access-key", secretAccessKey = null),
                AwsStorageProperties(accessKeyId = null, secretAccessKey = "test-secret-key"),
                AwsStorageProperties(accessKeyId = " ", secretAccessKey = "test-secret-key"),
                AwsStorageProperties(accessKeyId = "test-access-key", secretAccessKey = ""),
            )

        incomplete.forEach { properties ->
            assertThat(config.awsCredentialsProvider(properties)).isInstanceOf(DefaultCredentialsProvider::class.java)
        }
    }

    @Test
    fun `s3 client overrides the endpoint only when one is configured`() {
        val custom =
            AwsStorageProperties(s3 = AwsS3Properties(bucket = "test-bucket", endpoint = "http://localhost:9000"))
        val blank = AwsStorageProperties(s3 = AwsS3Properties(bucket = "test-bucket", endpoint = "  "))

        config.s3Client(custom, staticCredentials).use { client ->
            assertThat(client.serviceClientConfiguration().endpointOverride())
                .contains(URI.create("http://localhost:9000"))
        }
        config.s3Client(blank, staticCredentials).use { client ->
            assertThat(client.serviceClientConfiguration().endpointOverride()).isEmpty()
        }
    }

    @Test
    fun `presigned urls target the configured endpoint with path style access`() {
        val custom =
            AwsStorageProperties(
                s3 =
                    AwsS3Properties(
                        bucket = "test-bucket",
                        endpoint = "http://localhost:9000",
                        pathStyleAccess = true,
                    ),
            )

        val url = config.s3Presigner(custom, staticCredentials).use { presignedUrl(it) }

        assertThat(url).startsWith("http://localhost:9000/test-bucket/images/1/photo.jpg?")
    }

    @Test
    fun `presigned urls target the regional virtual host without an endpoint`() {
        val defaults = AwsStorageProperties(s3 = AwsS3Properties(bucket = "test-bucket", endpoint = null))

        val url = config.s3Presigner(defaults, staticCredentials).use { presignedUrl(it) }

        assertThat(url).startsWith("https://test-bucket.s3.ap-northeast-2.amazonaws.com/images/1/photo.jpg?")
    }

    private fun presignedUrl(presigner: S3Presigner): String =
        presigner
            .presignGetObject(
                GetObjectPresignRequest
                    .builder()
                    .signatureDuration(Duration.ofMinutes(5))
                    .getObjectRequest { it.bucket("test-bucket").key("images/1/photo.jpg") }
                    .build(),
            ).url()
            .toString()
}
