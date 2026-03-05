package com.realteeth.assignment.application.service

import com.realteeth.assignment.application.dto.CreateTaskRequest
import com.realteeth.assignment.application.dto.CreateTaskResponse
import com.realteeth.assignment.application.dto.TaskListResponse
import com.realteeth.assignment.application.dto.TaskResponse
import com.realteeth.assignment.domain.entity.TaskStatus

interface ImageTaskService {

    /**
     * 이미지 처리 작업 생성
     * - 멱등성: 동일한 idempotencyKey로 요청 시 기존 작업 반환
     * - Transaction Outbox 패턴: Task 생성과 Outbox를 동일 트랜잭션에 저장
     */
    fun createImageTask(request: CreateTaskRequest): CreateTaskResponse

    /**
     * 2. 작업 상태 조회
     */
    fun getTask(taskId: String): TaskResponse

    /**
     * 모든 작업 목록 조회
     */
    fun getAllTasks(): TaskListResponse

    /**
     * 특정 상태의 작업 목록 조회
     */
    fun getTasksByStatus(statuses: List<TaskStatus>): TaskListResponse

    /**
     * Task 상태를 PROCESSING으로 업데이트(Mock Worker 호출 성공 시)
     */
    fun updateTaskToProcessing(taskId: String, mockJobId: String)

    /**
     * Task 완료 처리
     */
    fun completeTask(taskId: String, result: String)

    /**
     * Task 실패 처리
     */
    fun failTask(taskId: String, errorMessage: String)

    /**
     * Task 재시도 횟수 증가 및 재시도 가능 여부 반환
     */
    fun incrementRetryAndCheck(taskId: String) : Boolean
}