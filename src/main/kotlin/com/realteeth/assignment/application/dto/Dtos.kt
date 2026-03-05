package com.realteeth.assignment.application.dto

import com.realteeth.assignment.domain.entity.ImageTask
import com.realteeth.assignment.domain.entity.TaskStatus
import org.jetbrains.annotations.NotNull
import java.time.LocalDateTime

// === Request/Response ===
data class CreateTaskRequest(
    @field:NotNull(value = "Image Url 은 필수 값입니다.")
    val imageUrl: String,

    @field:NotNull(value = "idempotencyKey 는 필수 값입니다.")
    val idempotencyKey: String
)

data class CreateTaskResponse(
    val taskId: String,
    val status: TaskStatus,
    val message: String
)

data class TaskResponse(
    val taskId: String,
    val status: TaskStatus,
    val result: String?,
    val errorMessage: String?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
) {
    companion object {
        fun from(task: ImageTask): TaskResponse {
            return TaskResponse(
                taskId = task.taskId,
                status = task.status,
                result = task.result,
                errorMessage = task.errorMessage,
                createdAt = task.createdAt,
                updatedAt = task.updatedAt
            )
        }
    }
}

data class TaskListResponse(
    val tasks: List<TaskResponse>,
    val totalCount: Int
)

// === Mock Worker Dto ===
data class MockProcessRequest(
    val imageUrl: String
)

data class MockProcessResponse(
    val jobId: String,
    val status: String
)

data class MockJobStatusResponse(
    val jobId: String,
    val status: String,
    val result: String?
)

// === Outbox ===
data class TaskCreatedPayload(
    val taskId: String,
    val imageUrl: String
)
