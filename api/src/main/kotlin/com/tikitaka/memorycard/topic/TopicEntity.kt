package com.tikitaka.memorycard.topic

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "topics")
open class TopicEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null,

    @Column(nullable = false, length = 100)
    open var title: String = "",

    @Column(columnDefinition = "TEXT")
    open var description: String? = null,

    @Column(name = "sort_order")
    open var sortOrder: Int = 0,

    @Column(name = "created_at", insertable = false, updatable = false)
    open var createdAt: LocalDateTime? = null,

    @Column(name = "updated_at", insertable = false)
    open var updatedAt: LocalDateTime? = null,
)
