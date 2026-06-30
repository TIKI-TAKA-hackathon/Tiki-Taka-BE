package com.tikitaka.memorycard.topic

import java.math.BigDecimal

data class TopicResponse(
    val id: Long,
    val title: String,
    val description: String?,
    val sortOrder: Int,
)

data class QuestionResponse(
    val id: Long,
    val topicId: Long,
    val displayText: String,
    val ttsText: String,
    val audioUrl: String?,
    val readSpeed: BigDecimal,
    val sortOrder: Int,
)
