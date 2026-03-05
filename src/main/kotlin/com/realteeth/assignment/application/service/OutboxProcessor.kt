package com.realteeth.assignment.application.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.realteeth.assignment.domain.repository.OutboxRepository
import com.realteeth.assignment.infrastructure.client.MockWorkerClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class OutboxProcessor(
    private val outboxRepository: OutboxRepository,
    private val mockWorkerClient: MockWorkerClient,
    private val imageTaskService: ImageTaskService,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(javaClass)

}