package com.realteeth.assignment.domain.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(
    name = "outbox",
    indexes = [
        Index(name = "idx_outbox_status", columnList = "status"),
        Index(name = "idx_outbox_created_at", columnList = "createdAt"),
    ])
class Outbox(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, length = 50)
    val aggregateType: String,

    @Column(nullable = false, length = 36)
    val aggregateId: String,

    @Column(nullable = false, length = 50)
    val eventType: String,

    @Column(nullable = false, columnDefinition = "JSON")
    val payload: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: OutboxStatus = OutboxStatus.PENDING,

    @Column(nullable = false)
    var retryCount: Int = 0,

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column
    var processedAt: LocalDateTime? = null,
) {
    companion object {
        const val MAX_RETRY_COUNT = 5
        const val RETRY_INTERVAL_SECOND = 30L

        fun createTaskCreatedEvent(taskId: String,
                                   payload: String
        ): Outbox {
            return Outbox(
                aggregateType = "ImageTask",
                aggregateId = taskId,
                eventType = "TASK_CREATED",
                payload = payload,
            )
        }
    }

    fun markAsProcessed() {
        status = OutboxStatus.PROCESSED
        processedAt = LocalDateTime.now()
    }

    fun markAsFailed() {
        status = OutboxStatus.FAILED
        processedAt = LocalDateTime.now()
    }

    fun incrementRetryCount(): Boolean {
        retryCount++
        return retryCount <= MAX_RETRY_COUNT
    }

    fun canRetry(): Boolean = retryCount < MAX_RETRY_COUNT && status == OutboxStatus.PENDING
}