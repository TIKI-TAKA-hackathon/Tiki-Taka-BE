package com.tikitaka.memorycard.topic

import com.tikitaka.memorycard.config.AppProperties
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class TopicService(
    private val topicRepository: TopicRepository,
    private val questionTemplateRepository: QuestionTemplateRepository,
    private val appProperties: AppProperties,
) {
    fun getTopics(): List<TopicResponse> =
        topicRepository.findAllByOrderBySortOrderAscIdAsc().map {
            TopicResponse(
                id = it.id ?: 0,
                title = it.title,
                description = it.description,
                sortOrder = it.sortOrder,
            )
        }

    fun getQuestions(topicId: Long): List<QuestionResponse> {
        if (!topicRepository.existsById(topicId)) {
            throw NoSuchElementException("Topic not found: $topicId")
        }

        return questionTemplateRepository.findByTopicIdAndIsActiveTrueOrderBySortOrderAscIdAsc(topicId)
            .map {
                QuestionResponse(
                    id = it.id ?: 0,
                    topicId = it.topicId,
                    displayText = it.displayText,
                    ttsText = it.ttsText,
                    audioUrl = buildAudioUrl(it.audioFileName),
                    readSpeed = it.readSpeed,
                    sortOrder = it.sortOrder,
                )
            }
    }

    private fun buildAudioUrl(audioFileName: String?): String? {
        val fileName = audioFileName?.takeIf { it.isNotBlank() } ?: return null
        return "${appProperties.frontendBaseUrlWithoutTrailingSlash()}/audio/questions/$fileName"
    }
}
