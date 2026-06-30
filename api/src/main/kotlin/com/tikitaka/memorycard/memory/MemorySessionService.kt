package com.tikitaka.memorycard.memory

import com.tikitaka.memorycard.config.AppProperties
import com.tikitaka.memorycard.shared.TokenGenerator
import com.tikitaka.memorycard.topic.TopicRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class MemorySessionService(
    private val topicRepository: TopicRepository,
    private val sessionRepository: MemorySessionRepository,
    private val answerRepository: MemoryAnswerRepository,
    private val cardRepository: MemoryCardRepository,
    private val tokenGenerator: TokenGenerator,
    private val appProperties: AppProperties,
) {
    fun createSession(request: CreateMemorySessionRequest): CreateMemorySessionResponse {
        if (!topicRepository.existsById(request.topicId)) {
            throw NoSuchElementException("Topic not found: ${request.topicId}")
        }

        val session = sessionRepository.save(
            MemorySessionEntity(
                topicId = request.topicId,
                interviewerName = request.interviewerName,
                elderName = request.elderName,
                shareToken = generateUniqueSessionToken(),
                consentChecked = request.consentChecked,
            ),
        )

        return CreateMemorySessionResponse(
            sessionId = session.id ?: 0,
            shareToken = session.shareToken,
        )
    }

    fun saveAnswer(sessionId: Long, request: SaveAnswerRequest): SaveAnswerResponse {
        if (request.question.isBlank()) {
            throw IllegalArgumentException("Question must not be blank")
        }
        if (request.answer.isBlank()) {
            throw IllegalArgumentException("Answer must not be blank")
        }
        if (!sessionRepository.existsById(sessionId)) {
            throw NoSuchElementException("Memory session not found: $sessionId")
        }

        val nextSortOrder = answerRepository.countBySessionId(sessionId).toInt() + 1
        val answer = answerRepository.save(
            MemoryAnswerEntity(
                sessionId = sessionId,
                question = request.question,
                answer = request.answer,
                youthReply = request.youthReply,
                emotionTag = request.emotionTag,
                sortOrder = nextSortOrder,
            ),
        )

        return SaveAnswerResponse(answerId = answer.id ?: 0)
    }

    fun createCardDraft(sessionId: Long): CardDraftResponse {
        val session = sessionRepository.findById(sessionId)
            .orElseThrow { NoSuchElementException("Memory session not found: $sessionId") }
        val topic = topicRepository.findById(session.topicId)
            .orElseThrow { NoSuchElementException("Topic not found: ${session.topicId}") }
        val firstAnswer = answerRepository.findBySessionIdOrderBySortOrderAscIdAsc(sessionId).firstOrNull()

        val emotionTag = firstAnswer?.emotionTag?.takeIf { it.isNotBlank() } ?: "기억"
        val tags = listOf(normalizeTag(topic.title), normalizeTag(emotionTag)).distinct()
        val card = cardRepository.save(
            MemoryCardEntity(
                sessionId = sessionId,
                cardTitle = "${topic.title}의 기억",
                oneLineSummary = "${emotionTag}이 담긴 ${topic.title} 이야기",
                elderQuote = firstAnswer?.answer?.let(::firstSentence),
                youthReply = firstAnswer?.youthReply,
                tags = tags.joinToString(","),
                shareToken = generateUniqueCardToken(),
            ),
        )

        return card.toDraftResponse()
    }

    fun updateCard(cardId: Long, request: UpdateMemoryCardRequest): MemoryCardResponse {
        val card = cardRepository.findById(cardId)
            .orElseThrow { NoSuchElementException("Memory card not found: $cardId") }

        request.cardTitle?.takeIf { it.isNotBlank() }?.let { card.cardTitle = it }
        request.oneLineSummary?.let { card.oneLineSummary = it }
        request.elderQuote?.let { card.elderQuote = it }
        request.youthReply?.let { card.youthReply = it }
        request.tags?.let { card.tags = it.map(::normalizeTag).filter(String::isNotBlank).joinToString(",") }
        request.templateKey?.takeIf { it.isNotBlank() }?.let { card.templateKey = it }

        return cardRepository.save(card).toResponse()
    }

    fun publishCard(cardId: Long): PublishCardResponse {
        val card = cardRepository.findById(cardId)
            .orElseThrow { NoSuchElementException("Memory card not found: $cardId") }
        val session = sessionRepository.findById(card.sessionId)
            .orElseThrow { NoSuchElementException("Memory session not found: ${card.sessionId}") }

        card.isPublished = true
        session.status = "PUBLISHED"
        session.visibility = "LINK_ONLY"

        cardRepository.save(card)
        sessionRepository.save(session)

        return PublishCardResponse(
            shareUrl = "${appProperties.frontendBaseUrlWithoutTrailingSlash()}/t/${card.shareToken}",
        )
    }

    @Transactional(readOnly = true)
    fun getSharedCard(shareToken: String): SharedCardResponse {
        val card = cardRepository.findByShareToken(shareToken)
            .orElseThrow { NoSuchElementException("Shared card not found") }
        if (!card.isPublished) {
            throw NoSuchElementException("Shared card not found")
        }
        val session = sessionRepository.findById(card.sessionId)
            .orElseThrow { NoSuchElementException("Memory session not found: ${card.sessionId}") }

        return SharedCardResponse(
            cardId = card.id ?: 0,
            topicId = session.topicId,
            cardTitle = card.cardTitle,
            oneLineSummary = card.oneLineSummary,
            elderQuote = card.elderQuote,
            youthReply = card.youthReply,
            tags = card.tagsList(),
        )
    }

    private fun generateUniqueSessionToken(): String {
        repeat(5) {
            val token = tokenGenerator.generate()
            if (!sessionRepository.existsByShareToken(token)) {
                return token
            }
        }
        throw IllegalStateException("Failed to generate unique session token")
    }

    private fun generateUniqueCardToken(): String {
        repeat(5) {
            val token = tokenGenerator.generate()
            if (!cardRepository.existsByShareToken(token)) {
                return token
            }
        }
        throw IllegalStateException("Failed to generate unique card token")
    }

    private fun firstSentence(value: String): String {
        val trimmed = value.trim()
        val match = Regex("""^(.+?[.!?。！？])""").find(trimmed)
        return match?.groupValues?.get(1) ?: trimmed
    }

    private fun normalizeTag(value: String): String = value.replace("\\s+".toRegex(), "").trim()

    private fun MemoryCardEntity.toDraftResponse(): CardDraftResponse =
        CardDraftResponse(
            cardId = id ?: 0,
            cardTitle = cardTitle,
            oneLineSummary = oneLineSummary,
            elderQuote = elderQuote,
            youthReply = youthReply,
            tags = tagsList(),
            shareToken = shareToken,
        )

    private fun MemoryCardEntity.toResponse(): MemoryCardResponse =
        MemoryCardResponse(
            cardId = id ?: 0,
            cardTitle = cardTitle,
            oneLineSummary = oneLineSummary,
            elderQuote = elderQuote,
            youthReply = youthReply,
            tags = tagsList(),
            templateKey = templateKey,
            shareToken = shareToken,
            isPublished = isPublished,
        )

    private fun MemoryCardEntity.tagsList(): List<String> =
        tags.orEmpty()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
}
