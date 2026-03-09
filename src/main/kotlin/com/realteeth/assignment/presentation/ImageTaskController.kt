package com.realteeth.assignment.presentation

import com.realteeth.assignment.application.dto.CreateTaskRequest
import com.realteeth.assignment.application.dto.CreateTaskResponse
import com.realteeth.assignment.application.dto.TaskListResponse
import com.realteeth.assignment.application.dto.TaskResponse
import com.realteeth.assignment.domain.entity.TaskStatus
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam

@Tag(name = "이미지 처리 작업", description = "이미지 처리 작업 API")
interface ImageTaskController {

    @Operation(
        summary = "[1.1] 이미지 처리 작업 요청",
        description = """
            POST /api/v1/tasks
            
            새로운 이미지 처리 작업을 요청한다.
            - 동일한 idempotencyKey로 중복 요청 시 기존 작업을 반환한다.
            - 작업은 비동기로 처리되며, 생성 즉시 PENDING 상태를 반환한다.
        """
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "202",
            description = "작업 요청 성공",
            content = [Content(schema = Schema(implementation = CreateTaskResponse::class))]
        ),
        ApiResponse(
            responseCode = "400",
            description = "잘못된 요청",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))]
        )
    )
    fun createTask(@Valid @RequestBody request: CreateTaskRequest): ResponseEntity<CreateTaskResponse>

    @Operation(
        summary = "[1.2] 이미지 처리 작업 상태 조회",
        description = """
            GET /api/v1/tasks/{taskId}
            
            TaskId로 특정 작업의 상태/결과를 조회한다.
        """
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = [Content(schema = Schema(implementation = TaskResponse::class))]
        ),
        ApiResponse(
            responseCode = "404",
            description = "작업을 찾을 수 없음",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))]
        )
    )
    fun getTask(@PathVariable taskId: String): ResponseEntity<TaskResponse>

    @Operation(
        summary = "[1.3] 모든 이미지 처리 작업 목록 조회",
        description = """
            GET /api/v1/tasks
            
            모든 작업 목록을 조회합니다.
            - 작업의 status로 필터링이 가능하다.
        """
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = [Content(schema = Schema(implementation = TaskListResponse::class))]
        )
    )
    fun getAllTasks(
        @Parameter()
        @RequestParam(required = false) status: List<TaskStatus>?
    ): ResponseEntity<TaskListResponse>
}