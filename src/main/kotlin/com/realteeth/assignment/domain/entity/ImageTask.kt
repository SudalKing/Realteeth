package com.realteeth.assignment.domain.entity

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(
    name = "image_task",
    indexes = [
        Index(name = "idx_task_id", columnList = "taskId", unique = true),
        Index(name = "idx_idempotency_key", columnList = "idempotencyKey", unique = true),
        Index(name = "idx_status", columnList = "status")
    ]
)
class ImageTask(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, unique = true, length = 36)
    val taskId: String = UUID.randomUUID().toString(),

    @Column(nullable = false, length = 2048)
    val imageUrl: String,

    @Column(nullable = false, unique = true, length = 64)
    val idempotencyKey: String,

    @Column(nullable = false, length = 20, columnDefinition = "VARCHAR(20)")
    var status: TaskStatus = TaskStatus.PENDING,

    @Column(length = 100)
    var mockJobId: String? = null,

    @Column(columnDefinition = "TEXT")
    var result: String? = null,

    @Column(columnDefinition = "TEXT")
    var errorMessage: String? = null,

    @Column(nullable = false)
    var retryCount: Int = 0,

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    companion object {
        const val MAX_RETRY_COUNT = 3
    }

    fun transitionTo(newStatus: TaskStatus) {
        require(status.canTransitionTo(newStatus)) {
            "유효하지 않은 상태 변경입니다: $status -> $newStatus"
        }
        status = newStatus
        updatedAt = LocalDateTime.now()
    }

    fun markAsProcessing(mockJobId: String) {
        transitionTo(TaskStatus.PROCESSING)
        this.mockJobId = mockJobId
    }

    fun markAsCompleted(result: String) {
        transitionTo(TaskStatus.COMPLETED)
        this.result = result
    }

    fun markAsFailed(errorMessage: String) {
        transitionTo(TaskStatus.FAILED)
        this.errorMessage = errorMessage
    }

    fun incrementRetryCount(): Boolean {
        retryCount++
        updatedAt = LocalDateTime.now()

        return retryCount <= MAX_RETRY_COUNT
    }

    fun canRetry(): Boolean = retryCount < MAX_RETRY_COUNT
}