package com.tikitaka.memorycard.topic

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "question_templates")
open class QuestionTemplateEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null,

    @Column(name = "topic_id", nullable = false)
    open var topicId: Long = 0,

    @Column(name = "display_text", nullable = false, columnDefinition = "TEXT")
    open var displayText: String = "",

    @Column(name = "tts_text", nullable = false, columnDefinition = "TEXT")
    open var ttsText: String = "",

    @Column(name = "audio_file_name")
    open var audioFileName: String? = null,

    @Column(name = "read_speed")
    open var readSpeed: BigDecimal = BigDecimal("0.85"),

    @Column(name = "sort_order")
    open var sortOrder: Int = 0,

    @Column(name = "is_active")
    open var isActive: Boolean = true,

    @Column(name = "created_at", insertable = false, updatable = false)
    open var createdAt: LocalDateTime? = null,

    @Column(name = "updated_at", insertable = false)
    open var updatedAt: LocalDateTime? = null,
)
