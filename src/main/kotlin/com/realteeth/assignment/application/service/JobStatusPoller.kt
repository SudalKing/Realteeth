package com.realteeth.assignment.application.service

import com.realteeth.assignment.domain.entity.TaskStatus
import com.realteeth.assignment.domain.repository.ImageTaskRepository
import com.realteeth.assignment.infrastructure.client.MockWorkerClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class JobStatusPoller(
    private val imageTaskRepository: ImageTaskRepository,
    private val imageTaskService: ImageTaskService,
    private val mockWorkerClient: MockWorkerClient
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * PROCESSING 상태의 모든 작업 폴링
     */
    @Transactional
    fun pollAllProcessingJobs() {
        val processingTasks = imageTaskRepository.findAllByStatusIn(listOf(TaskStatus.PROCESSING))
        log.debug("[ImageTask] behavior: 처리 중인 작업 조회 | SUCCESS | message: 처리 중인 작업 ${processingTasks.size}건")

        processingTasks.forEach { task ->
            task.mockJobId?.let { jobId ->
                pollJobStatus(task.taskId, jobId)
            }
        }
    }

    /**
     * 단일 작업 상태 폴링
     */
    fun pollJobStatus(taskId: String, mockJobId: String) {
        try {
            val result = mockWorkerClient.getJobStatus(mockJobId)

            result.fold(
                onSuccess = { response ->
                    when (response.status.uppercase()) {
                        "COMPLETED" -> {
                            imageTaskService.completeTask(taskId, response.result ?: "")
                            log.info("[ImageTask] behavior: 처리 중인 작업 조회 | SUCCESS | taskId: $taskId | mockJobId: $mockJobId | message: 작업 완료")
                        }
                        "FAILED" -> {
                            imageTaskService.failTask(taskId, "Mock Worker 서버에서 작업에 실패했습니다.")
                            log.info("[ImageTask] behavior: 처리 중인 작업 조회 | SUCCESS | taskId: $taskId | mockJobId: $mockJobId | message: 작업 실패")
                        }
                        "PROCESSING" -> {
                            log.debug("[ImageTask] behavior: 처리 중인 작업 조회 | SUCCESS | taskId: $taskId | mockJobId: $mockJobId | message: 작업 처리 중")
                        }
                        else -> {
                            log.warn("[ImageTask] behavior: 처리 중인 작업 조회 | SUCCESS | taskId: $taskId | mockJobId: $mockJobId | status: ${response.status} | message: 알 수 없는 처리 상태")
                        }
                    }
                },
                onFailure = { e ->
                    log.error("[ImageTask] behavior: 처리 중인 작업 조회 | FAIL | taskId: $taskId | mockJobId: $mockJobId | message: Mock Worker 서버 -> 작업 조회 실패", e)
                }
            )
        } catch (e: Exception) {
            log.error("[ImageTask] behavior: 처리 중인 작업 조회 | FAIL | taskId: $taskId | mockJobId: $mockJobId | message: 작업 조회 폴링 실패", e)
        }
    }
}