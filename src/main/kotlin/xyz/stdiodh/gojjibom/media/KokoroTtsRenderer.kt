package xyz.stdiodh.gojjibom.media

import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

@Component
class KokoroTtsRenderer(
    private val properties: KokoroTtsProperties,
) : TtsRenderer {
    override fun render(
        voice: String,
        text: String,
    ): RenderedTts {
        val url = properties.url.takeIf { it.isNotBlank() } ?: ttsWorkerNotConfigured()
        val restClient = RestClient.builder().baseUrl(url.trimEnd('/')).build()

        return try {
            val response =
                restClient
                    .post()
                    .uri("/render")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_OCTET_STREAM, MediaType.valueOf("audio/mpeg"))
                    .body(KokoroRenderRequest(voice = voice, text = text))
                    .retrieve()
                    .toEntity(ByteArray::class.java)
            val mp3Bytes = response.body ?: emptyTtsResponse()
            val durationMs = response.headers.getFirst(DURATION_HEADER)?.toIntOrNull() ?: 0
            RenderedTts(mp3Bytes = mp3Bytes, durationMs = durationMs)
        } catch (_: RestClientException) {
            ttsWorkerUnavailable()
        }
    }

    private fun ttsWorkerNotConfigured(): Nothing =
        throw MediaErrors.serviceUnavailable("TTS_WORKER_NOT_CONFIGURED", "TTS worker URL is not configured")

    private fun emptyTtsResponse(): Nothing =
        throw MediaErrors.serviceUnavailable("TTS_WORKER_EMPTY_RESPONSE", "TTS worker returned no audio")

    private fun ttsWorkerUnavailable(): Nothing =
        throw MediaErrors.serviceUnavailable("TTS_WORKER_UNAVAILABLE", "TTS worker request failed")

    private data class KokoroRenderRequest(
        val voice: String,
        val text: String,
    )

    private companion object {
        private const val DURATION_HEADER = "X-Duration-Ms"
    }
}
