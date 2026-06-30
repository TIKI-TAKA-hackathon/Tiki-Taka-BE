package com.tikitaka.memorycard.topic

import org.springframework.data.jpa.repository.JpaRepository

interface TopicRepository : JpaRepository<TopicEntity, Long> {
    fun findAllByOrderBySortOrderAscIdAsc(): List<TopicEntity>
}

interface QuestionTemplateRepository : JpaRepository<QuestionTemplateEntity, Long> {
    fun findByTopicIdAndIsActiveTrueOrderBySortOrderAscIdAsc(topicId: Long): List<QuestionTemplateEntity>
}
