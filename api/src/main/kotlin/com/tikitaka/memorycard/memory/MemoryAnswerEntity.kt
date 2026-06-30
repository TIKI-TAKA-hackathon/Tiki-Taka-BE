package com.tikitaka.memorycard.memory

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "memory_answers")
open class MemoryAnswerEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null,

    @Column(name = "session_id", nullable = false)
    open var sessionId: Long = 0,

    @Column(nullable = false, columnDefinition = "TEXT")
    open var question: String = "",

    @Column(nullable = false, columnDefinition = "TEXT")
    open var answer: String = "",

    @Column(name = "youth_reply", columnDefinition = "TEXT")
    open var youthReply: String? = null,

    @Column(name = "emotion_tag", length = 50)
    open var emotionTag: String? = null,

    @Column(name = "sort_order")
    open var sortOrder: Int = 0,

    @Column(name = "created_at", insertable = false, updatable = false)
    open var createdAt: LocalDateTime? = null,
)
