package com.sudal.imageprocessor.presentation

import com.sudal.imageprocessor.application.dto.CreateTaskRequest
import com.sudal.imageprocessor.application.dto.CreateTaskResponse
import com.sudal.imageprocessor.application.dto.TaskListResponse
import com.sudal.imageprocessor.application.dto.TaskResponse
import com.sudal.imageprocessor.application.service.ImageTaskService
import com.sudal.imageprocessor.application.service.impl.ImageTaskServiceImpl.TaskNotFoundException
import com.sudal.imageprocessor.domain.entity.TaskStatus
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RequestMapping("/api/v1/tasks")
@RestController
class ImageTaskControllerImpl(
    private val imageTaskService: ImageTaskService
): ImageTaskController {

    @PostMapping
    override fun createTask(@Valid @RequestBody request: CreateTaskRequest): ResponseEntity<CreateTaskResponse> {
        val response = imageTaskService.createImageTask(request)

        return ResponseEntity.status(HttpStatus.ACCEPTED)
            .body(response)
    }

    @GetMapping("/{taskId}")
    override fun getTask(@PathVariable taskId: String): ResponseEntity<TaskResponse> {
        val response = imageTaskService.getTask(taskId)

        return ResponseEntity.ok(response)
    }

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