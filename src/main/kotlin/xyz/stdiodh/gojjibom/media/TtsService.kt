package xyz.stdiodh.gojjibom.media

import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.time.LocalTime
import java.time.OffsetDateTime

@Service
class TtsService(
    private val authorizer: MembershipAuthorizer,
    private val clips: TtsClipRepository,
    private val renderer: TtsRenderer,
    private val objectStorage: ObjectStorage,
    private val mediaProperties: MediaProperties,
) {
    fun getOrRenderClip(request: TtsClipRequest): TtsClipResponse {
        authorizer.requireActiveMember(request.careGroupId, request.actorUserId)

        val voice = normalizeVoice(request.voice)
        val text = normalizedTemplateText(request.doseLabel, request.scheduledTime)
        val textHash = hash(voice, text)
        val cached = clips.findByVoiceAndTextHash(voice, textHash)
        if (cached != null) {
            return cached.toResponse()
        }

        val rendered = renderer.render(voice, text)
        val objectKey = "tts/$voice/$textHash.mp3"
        objectStorage.putObject(
            objectKey = objectKey,
            contentType = TTS_CONTENT_TYPE,
            bytes = rendered.mp3Bytes,
        )

        val clip =
            clips.save(
                TtsClipEntity(
                    textHash = textHash,
                    voice = voice,
                    text = text,
                    objectKey = objectKey,
                    durationMs = rendered.durationMs,
                    createdAt = OffsetDateTime.now(),
                ),
            )

        return clip.toResponse()
    }

    private fun TtsClipEntity.toResponse(): TtsClipResponse {
        val presigned =
            objectStorage.presignView(
                objectKey = objectKey,
                ttlSeconds = mediaProperties.viewUrlTtlSeconds,
            )

        return TtsClipResponse(
            id = requiredId(),
            voice = voice,
            text = text,
            objectKey = objectKey,
            playUrl = presigned.url,
            expiresAt = presigned.expiresAt,
            durationMs = durationMs,
        )
    }

    private fun normalizeVoice(voice: String): String {
        val normalized = voice.trim().lowercase().replace(VOICE_ALLOWED_CHARS, "")
        if (normalized.isBlank()) {
            throw MediaErrors.badRequest("INVALID_TTS_VOICE", "TTS voice is invalid")
        }
        return normalized
    }

    private fun normalizedTemplateText(
        doseLabel: String,
        scheduledTime: LocalTime,
    ): String {
        val label = doseLabel.trim().replace(WHITESPACE, " ")
        if (label.isBlank()) {
            throw MediaErrors.badRequest("INVALID_TTS_DOSE_LABEL", "Dose label is required")
        }

        return "${scheduledTime.toKoreanText()}에 $label 복용 시간입니다. 약국에서 등록한 복약 정보 안내입니다."
            .replace(WHITESPACE, " ")
            .trim()
    }

    private fun LocalTime.toKoreanText(): String {
        val period = if (hour < NOON_HOUR) "오전" else "오후"
        val displayHour = hour.mod(NOON_HOUR).takeIf { it != 0 } ?: NOON_HOUR
        if (minute == 0) {
            return "$period ${displayHour}시"
        }
        return "$period ${displayHour}시 ${minute}분"
    }

    private fun hash(
        voice: String,
        text: String,
    ): String {
        val source = "$voice\n$text".toByteArray(Charsets.UTF_8)
        return MessageDigest
            .getInstance("SHA-256")
            .digest(source)
            .joinToString("") { "%02x".format(it) }
    }

    private companion object {
        private const val NOON_HOUR = 12
        private const val TTS_CONTENT_TYPE = "audio/mpeg"
        private val WHITESPACE = Regex("\\s+")
        private val VOICE_ALLOWED_CHARS = Regex("[^a-z0-9_-]")
    }
}
