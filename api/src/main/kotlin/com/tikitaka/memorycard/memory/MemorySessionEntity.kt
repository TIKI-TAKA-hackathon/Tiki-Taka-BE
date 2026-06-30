package com.tikitaka.memorycard.memory

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "memory_sessions")
open class MemorySessionEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null,

    @Column(name = "topic_id", nullable = false)
    open var topicId: Long = 0,

    @Column(name = "interviewer_name", length = 50)
    open var interviewerName: String? = null,

    @Column(name = "elder_name", length = 50)
    open var elderName: String? = null,

    @Column(length = 30)
    open var status: String = "DRAFT",

    @Column(length = 30)
    open var visibility: String = "PRIVATE",

    @Column(name = "share_token", nullable = false, unique = true, length = 100)
    open var shareToken: String = "",

    @Column(name = "consent_checked")
    open var consentChecked: Boolean = false,

    @Column(name = "created_at", insertable = false, updatable = false)
    open var createdAt: LocalDateTime? = null,

    @Column(name = "updated_at", insertable = false)
    open var updatedAt: LocalDateTime? = null,
)
