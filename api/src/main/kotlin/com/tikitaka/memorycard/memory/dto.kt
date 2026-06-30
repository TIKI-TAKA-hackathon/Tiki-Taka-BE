package com.tikitaka.memorycard.memory

data class CreateMemorySessionRequest(
    val topicId: Long,
    val interviewerName: String? = null,
    val elderName: String? = null,
    val consentChecked: Boolean = false,
)

data class CreateMemorySessionResponse(
    val sessionId: Long,
    val shareToken: String,
)

data class SaveAnswerRequest(
    val question: String,
    val answer: String,
    val youthReply: String? = null,
    val emotionTag: String? = null,
)

data class SaveAnswerResponse(
    val answerId: Long,
)

data class CardDraftResponse(
    val cardId: Long,
    val cardTitle: String,
    val oneLineSummary: String?,
    val elderQuote: String?,
    val youthReply: String?,
    val tags: List<String>,
    val shareToken: String,
)

data class UpdateMemoryCardRequest(
    val cardTitle: String? = null,
    val oneLineSummary: String? = null,
    val elderQuote: String? = null,
    val youthReply: String? = null,
    val tags: List<String>? = null,
    val templateKey: String? = null,
)

data class MemoryCardResponse(
    val cardId: Long,
    val cardTitle: String,
    val oneLineSummary: String?,
    val elderQuote: String?,
    val youthReply: String?,
    val tags: List<String>,
    val templateKey: String,
    val shareToken: String,
    val isPublished: Boolean,
)

data class PublishCardResponse(
    val shareUrl: String,
)

data class SharedCardResponse(
    val cardId: Long,
    val topicId: Long,
    val cardTitle: String,
    val oneLineSummary: String?,
    val elderQuote: String?,
    val youthReply: String?,
    val tags: List<String>,
)
