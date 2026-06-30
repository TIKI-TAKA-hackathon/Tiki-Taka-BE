package com.tikitaka.memorycard.topic

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/topics")
class TopicController(
    private val topicService: TopicService,
) {
    @GetMapping
    fun getTopics(): List<TopicResponse> = topicService.getTopics()

    @GetMapping("/{topicId}/questions")
    fun getQuestions(@PathVariable topicId: Long): List<QuestionResponse> =
        topicService.getQuestions(topicId)
}
