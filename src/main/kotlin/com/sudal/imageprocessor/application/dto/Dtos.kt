package com.sudal.imageprocessor.application.dto

import com.sudal.imageprocessor.domain.entity.ImageTask
import com.sudal.imageprocessor.domain.entity.TaskStatus
import io.swagger.v3.oas.annotations.media.Schema
import org.jetbrains.annotations.NotNull
import java.time.LocalDateTime

// === Request/Response ===
@Schema(description = "이미지 처리 작업 요청")
data class CreateTaskRequest(
    @param:Schema(
        description = "처리할 이미지 URL",
        example = "https://example.com/image.jpg",
        required = true,
    )
    @field:NotNull(value = "Image Url 은 필수 값입니다.")
    val imageUrl: String,

    @param:Schema(
        description = "중복 요청 방지를 위한 고유 키 (클라이언트에서 생성)",
        example = "req-1111-2222-3333",
        required = true,
    )
    @field:NotNull(value = "idempotencyKey 는 필수 값입니다.")
    val idempotencyKey: String
)

@Schema(description = "작업 요청 응답")
data class CreateTaskResponse(
    @param:Schema(description = "생성된 작업 ID", example = "UUID")
    val taskId: String,

    @param:Schema(description = "작업 상태", example = "PENDING/PROCESSING/...")
    val status: TaskStatus,

    @param:Schema(description = "응답 메시지",
        example = "- 작업이 생성되었습니다." +
                "-작업이 중복 요청되어 기존 작업을 반환합니다.")
    val message: String
)

@Schema(description = "작업 상태 응답")
data class TaskResponse(
    @param:Schema(description = "작업 ID", example = "UUID")
    val taskId: String,

    @param:Schema(description = "작업 상태", example = "PENDING/PROCESSING/...")
    val status: TaskStatus,

    @param:Schema(description = "처리 결과 (완료 시)")
    val result: String?,

    @param:Schema(description = "에러 메시지 (실패 시)")
    val errorMessage: String?,

    @param:Schema(description = "생성 시각")
    val createdAt: LocalDateTime,

    @param:Schema(description = "수정 시각")
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

@Schema(description = "작업 목록 응답")
data class TaskListResponse(
    @param:Schema(description = "작업 목록")
    val tasks: List<TaskResponse>,

    @param:Schema(description = "전체 작업 수", example = "5")
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
