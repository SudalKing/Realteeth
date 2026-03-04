package com.realteeth.assignment.application.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service

@Service
class ImageTaskService(
    private val imageTaskRepository: ImageTaskRepository,
    private val outboxRepository: OutboxRepository,
    private val objectMapper: ObjectMapper,
    private val eventPublisher: ApplicationEventPublisher
) {
}