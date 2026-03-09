package com.realteeth.assignment.presentation

import com.realteeth.assignment.application.dto.CreateTaskRequest
import com.realteeth.assignment.application.dto.CreateTaskResponse
import com.realteeth.assignment.application.dto.TaskListResponse
import com.realteeth.assignment.application.dto.TaskResponse
import com.realteeth.assignment.application.service.ImageTaskService
import com.realteeth.assignment.application.service.impl.ImageTaskServiceImpl.TaskNotFoundException
import com.realteeth.assignment.domain.entity.TaskStatus
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RequestMapping("/api/v1/tasks")
@RestController
class ImageTaskControllerImpl(
    private val imageTaskService: ImageTaskService
): ImageTaskController {

    /**
     * [1.1] 이미지 처리 작업 요청
     * POST /api/v1/tasks
     */
    @PostMapping
    override fun createTask(@Valid @RequestBody request: CreateTaskRequest): ResponseEntity<CreateTaskResponse> {
        val response = imageTaskService.createImageTask(request)

        return ResponseEntity.status(HttpStatus.ACCEPTED)
            .body(response)
    }

    /**
     * [1.2] 작업 상태 조회
     * GET /api/v1/tasks/{taskId}
     */
    @GetMapping("/{taskId}")
    override fun getTask(@PathVariable taskId: String): ResponseEntity<TaskResponse> {
        val response = imageTaskService.getTask(taskId)

        return ResponseEntity.ok(response)
    }

    /**
     * [1.3] 모든 작업 목록 조회
     * GET /api/v1/tasks
     */
    @GetMapping
    override fun getAllTasks(@RequestParam(required = false) status: List<TaskStatus>?): ResponseEntity<TaskListResponse> {
        val response = if (status.isNullOrEmpty()) {
            imageTaskService.getAllTasks()
        } else {
            imageTaskService.getTasksByStatus(status)
        }

        return ResponseEntity.ok(response)
    }
}

@RestControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(TaskNotFoundException::class)
    fun handleTaskNotFound(e: TaskNotFoundException): ResponseEntity<ErrorResponse> {
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse("NOT_FOUND", e.message ?: "작업이 없습니다."))
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleBadRequest(e: IllegalArgumentException): ResponseEntity<ErrorResponse> {
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse("BAD_REQUEST", e.message ?: "잘못된 요청입니다."))
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(e: Exception): ResponseEntity<ErrorResponse> {
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse("INTERNAL_ERROR", "서버 오류가 발생했습니다."))
    }
}


data class ErrorResponse(
    val code: String,
    val message: String
)