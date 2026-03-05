package com.realteeth.assignment.domain.event

data class TaskCreatedEvent(
    val taskId: String,
    val imageUrl: String
)

data class OutboxEvent(
    val outboxId: Long,
    val taskId: String,
    val imageUrl: String
)
