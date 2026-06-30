package com.tikitaka.memorycard.memory

import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface MemorySessionRepository : JpaRepository<MemorySessionEntity, Long> {
    fun existsByShareToken(shareToken: String): Boolean
}

interface MemoryAnswerRepository : JpaRepository<MemoryAnswerEntity, Long> {
    fun countBySessionId(sessionId: Long): Long
    fun findBySessionIdOrderBySortOrderAscIdAsc(sessionId: Long): List<MemoryAnswerEntity>
}

interface MemoryCardRepository : JpaRepository<MemoryCardEntity, Long> {
    fun existsByShareToken(shareToken: String): Boolean
    fun findByShareToken(shareToken: String): Optional<MemoryCardEntity>
}
