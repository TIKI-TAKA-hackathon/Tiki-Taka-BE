package xyz.stdiodh.gojjibom.media

interface TtsRenderer {
    fun render(
        voice: String,
        text: String,
    ): RenderedTts
}

data class RenderedTts(
    val mp3Bytes: ByteArray,
    val durationMs: Int,
)
