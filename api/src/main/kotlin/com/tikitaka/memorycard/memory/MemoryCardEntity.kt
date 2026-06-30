package com.tikitaka.memorycard.memory

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "memory_cards")
open class MemoryCardEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null,

    @Column(name = "session_id", nullable = false)
    open var sessionId: Long = 0,

    @Column(name = "card_title", nullable = false, length = 150)
    open var cardTitle: String = "",

    @Column(name = "one_line_summary", columnDefinition = "TEXT")
    open var oneLineSummary: String? = null,

    @Column(name = "elder_quote", columnDefinition = "TEXT")
    open var elderQuote: String? = null,

    @Column(name = "youth_reply", columnDefinition = "TEXT")
    open var youthReply: String? = null,

    @Column(columnDefinition = "TEXT")
    open var tags: String? = null,

    @Column(name = "template_key", length = 50)
    open var templateKey: String = "BASIC",

    @Column(name = "share_token", nullable = false, unique = true, length = 100)
    open var shareToken: String = "",

    @Column(name = "is_published")
    open var isPublished: Boolean = false,

    @Column(name = "created_at", insertable = false, updatable = false)
    open var createdAt: LocalDateTime? = null,

    @Column(name = "updated_at", insertable = false)
    open var updatedAt: LocalDateTime? = null,
)
