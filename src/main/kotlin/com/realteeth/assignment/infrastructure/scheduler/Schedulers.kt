package com.realteeth.assignment.infrastructure.scheduler

import com.realteeth.assignment.application.service.OutboxProcessor
import com.realteeth.assignment.domain.repository.ImageTaskRepository
import com.realteeth.assignment.domain.repository.OutboxRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class OutboxScheduler(
    private val outboxRepository: OutboxRepository,
    private val outboxProcessor: OutboxProcessor
) {
    private val log = LoggerFactory.getLogger(javaClass)
}

@Component
class StuckRecoveryScheduler(
    private val imageTaskRepository: ImageTaskRepository,
    private val outboxRepository: OutboxRepository,
    private val outboxProcessor: OutboxProcessor
) {
    private val log = LoggerFactory.getLogger(javaClass)
}
