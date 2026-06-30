package com.tikitaka.memorycard.memory

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class MemorySessionController(
    private val memorySessionService: MemorySessionService,
) {
    @PostMapping("/api/v1/memory-sessions")
    fun createSession(@RequestBody request: CreateMemorySessionRequest): CreateMemorySessionResponse =
        memorySessionService.createSession(request)

    @PostMapping("/api/v1/memory-sessions/{sessionId}/answers")
    fun saveAnswer(
        @PathVariable sessionId: Long,
        @RequestBody request: SaveAnswerRequest,
    ): SaveAnswerResponse = memorySessionService.saveAnswer(sessionId, request)

    @PostMapping("/api/v1/memory-sessions/{sessionId}/card-draft")
    fun createCardDraft(@PathVariable sessionId: Long): CardDraftResponse =
        memorySessionService.createCardDraft(sessionId)

    @PatchMapping("/api/v1/memory-cards/{cardId}")
    fun updateCard(
        @PathVariable cardId: Long,
        @RequestBody request: UpdateMemoryCardRequest,
    ): MemoryCardResponse = memorySessionService.updateCard(cardId, request)

    @PostMapping("/api/v1/memory-cards/{cardId}/publish")
    fun publishCard(@PathVariable cardId: Long): PublishCardResponse =
        memorySessionService.publishCard(cardId)

    @GetMapping("/api/v1/shared-cards/{shareToken}")
    fun getSharedCard(@PathVariable shareToken: String): SharedCardResponse =
        memorySessionService.getSharedCard(shareToken)
}
