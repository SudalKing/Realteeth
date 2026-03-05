package com.realteeth.assignment.application.service.impl

import com.fasterxml.jackson.databind.ObjectMapper
import com.realteeth.assignment.application.dto.*
import com.realteeth.assignment.application.service.ImageTaskService
import com.realteeth.assignment.domain.entity.ImageTask
import com.realteeth.assignment.domain.entity.Outbox
import com.realteeth.assignment.domain.entity.TaskStatus
import com.realteeth.assignment.domain.event.TaskCreatedEvent
import com.realteeth.assignment.domain.repository.ImageTaskRepository
import com.realteeth.assignment.domain.repository.OutboxRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ImageTaskServiceImpl(
    private val imageTaskRepository: ImageTaskRepository,
    private val outboxRepository: OutboxRepository,
    private val objectMapper: ObjectMapper,
    private val eventPublisher: ApplicationEventPublisher
) : ImageTaskService {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun createImageTask(request: CreateTaskRequest): CreateTaskResponse {
        // 1. 이미 동일한 요청이 있다면 기존 작업 반환
        val existingTask = imageTaskRepository.findByIdempotencyKey(request.idempotencyKey)
        if (existingTask.isPresent) {
            val returnTask = existingTask.get()

            log.info("[ImageTask] behavior: 작업 요청 | SUCCESS | idempotencyKey: ${request.idempotencyKey} | taskId: ${returnTask.taskId} | message: 작업 중복 요청, 기존 작업 반환")

            return CreateTaskResponse(
                taskId = returnTask.taskId,
                status = returnTask.status,
                message = "작업이 중복 요청되어 기존 작업을 반환합니다."
            )
        }

        // 2. 새로운 요청
        // 2-1. 작업 생성
        val newTask = ImageTask(
            imageUrl = request.imageUrl,
            idempotencyKey = request.idempotencyKey
        )
        val savedImageTask = imageTaskRepository.save(newTask)
        log.info("[ImageTask] behavior: 작업 요청 | SUCCESS | idempotencyKey: ${savedImageTask.idempotencyKey} | taskId: ${savedImageTask.taskId} | message: 작업 생성")

        // 2-2. 아웃박스 생성
        val payload = objectMapper.writeValueAsString(
            TaskCreatedPayload(
                taskId = savedImageTask.taskId,
                imageUrl = savedImageTask.imageUrl,
            )
        )
        val outbox = Outbox.createTaskCreatedEvent(savedImageTask.taskId, payload)
        outboxRepository.save(outbox)
        log.info("[ImageTask] behavior: 작업 요청 | SUCCESS | id: ${outbox.id} | aggregateId: ${outbox.aggregateId} | message: 작업의 트랜잭션 아웃박스 이벤트 생성")

        // 2-3. 작업 생성 이벤트 발행
        eventPublisher.publishEvent(TaskCreatedEvent(savedImageTask.taskId, savedImageTask.imageUrl))

        return CreateTaskResponse(
            taskId = savedImageTask.taskId,
            status = savedImageTask.status,
            message = "작업이 생성되었습니다."
        )
    }

    @Transactional(readOnly = true)
    override fun getTask(taskId: String): TaskResponse {
        val task = imageTaskRepository.findByTaskId(taskId)
            .orElseThrow { TaskNotFoundException("작업을 찾을 수 없습니다: $taskId") }

        return TaskResponse.from(task)
    }

    @Transactional(readOnly = true)
    override fun getAllTasks(): TaskListResponse {
        val tasks = imageTaskRepository.findAll()
            .map { TaskResponse.from(it) }

        return TaskListResponse(tasks, tasks.size)
    }

    @Transactional(readOnly = true)
    override fun getTasksByStatus(statuses: List<TaskStatus>): TaskListResponse {
        val tasks = imageTaskRepository.findAllByStatusIn(statuses)
            .map { TaskResponse.from(it) }

        return TaskListResponse(tasks, tasks.size)
    }

    @Transactional
    override fun updateTaskToProcessing(taskId: String, mockJobId: String) {
        val task = imageTaskRepository.findByTaskIdWithLock(taskId)
            .orElseThrow { TaskNotFoundException("작업을 찾을 수 없습니다: $taskId") }

        if (task.status == TaskStatus.PENDING) {
            task.markAsProcessing(mockJobId)
            imageTaskRepository.save(task)
            log.info("[ImageTask] behavior: 작업 상태 변경 | SUCCESS | taskId: $taskId | mockJobId: $mockJobId | message: 작업 상태 변경 (PENDING -> PROCESSING)")
        } else {
            log.warn("[ImageTask] behavior: 작업 상태 변경 | FAIL | taskId: $taskId | status: ${task.status} | mockJobId: $mockJobId | message: PENDING 상태가 아닙니다.")
        }
    }

    @Transactional
    override fun completeTask(taskId: String, result: String) {
        val task = imageTaskRepository.findByTaskIdWithLock(taskId)
            .orElseThrow { TaskNotFoundException("작업을 찾을 수 없습니다: $taskId") }

        if (task.status == TaskStatus.PROCESSING) {
            task.markAsCompleted(result)
            imageTaskRepository.save(task)
            log.info("[ImageTask] behavior: 작업 상태 변경 | SUCCESS | taskId: $taskId | result: $result | message: 작업 상태 변경 (PROCESSING -> COMPLETED)")
        } else {
            log.warn("[ImageTask] behavior: 작업 상태 변경 | FAIL | taskId: $taskId | status: ${task.status} | result: $result | message: PROCESSING 상태가 아닙니다.")
        }
    }

    @Transactional
    override fun failTask(taskId: String, errorMessage: String) {
        val task = imageTaskRepository.findByTaskIdWithLock(taskId)
            .orElseThrow { TaskNotFoundException("작업을 찾을 수 없습니다: $taskId") }

        if (task.status in listOf(TaskStatus.PENDING, TaskStatus.PROCESSING)) {
            val prevStatus = task.status
            task.markAsFailed(errorMessage)
            imageTaskRepository.save(task)
            log.info("[ImageTask] behavior: 작업 상태 변경 | SUCCESS | taskId: $taskId | errorMessage: $errorMessage | message: 작업 상태 변경 ($prevStatus -> COMPLETED)")
        } else {
            log.warn("[ImageTask] behavior: 작업 상태 변경 | FAIL | taskId: $taskId | errorMessage: $errorMessage | message: 이미 ${task.status} 상태입니다.")
        }
    }

    @Transactional
    override fun incrementRetryAndCheck(taskId: String): Boolean {
        val task = imageTaskRepository.findByTaskIdWithLock(taskId)
            .orElseThrow { TaskNotFoundException("작업을 찾을 수 없습니다: $taskId") }

        val canRetry = task.incrementRetryCount()
        imageTaskRepository.save(task)

        if (!canRetry) {
            log.warn("[ImageTask] behavior: 재시도 횟수 증가 | FAIL | taskId: $taskId | retryCount: ${task.retryCount} | message: 재시도 횟수를 초과했습니다.")
            task.markAsFailed("재시도 횟수를 초과했습니다.")
            imageTaskRepository.save(task)
        }

        return canRetry
    }

    class TaskNotFoundException(message: String) : RuntimeException(message)
}