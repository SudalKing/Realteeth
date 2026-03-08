package com.realteeth.assignment.application.service

import com.realteeth.assignment.application.dto.MockJobStatusResponse
import com.realteeth.assignment.domain.entity.ImageTask
import com.realteeth.assignment.domain.entity.TaskStatus
import com.realteeth.assignment.domain.repository.ImageTaskRepository
import com.realteeth.assignment.infrastructure.client.MockWorkerClient
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
@DisplayName("JobStatusPoller 테스트")
class JobStatusPollerTest {

    @Mock
    private lateinit var imageTaskRepository: ImageTaskRepository

    @Mock
    private lateinit var imageTaskService: ImageTaskService

    @Mock
    private lateinit var mockWorkerClient: MockWorkerClient

    private lateinit var jobStatusPoller: JobStatusPoller

    @BeforeEach
    fun setUp() {
        jobStatusPoller = JobStatusPoller(
            imageTaskRepository,
            imageTaskService,
            mockWorkerClient
        )
    }

    @Nested
    @DisplayName("pollAllProcessingJobs")
    inner class PollAllProcessingJobs {

        @Test
        @DisplayName("PROCESSING 상태의 모든 작업을 폴링한다.")
        fun shouldPollAllProcessingTasks() {
            // given
            val task1 = createProcessingTask("task-1", "job-1")
            val task2 = createProcessingTask("task-2", "job-2")

            whenever(imageTaskRepository.findAllByStatusIn(listOf(TaskStatus.PROCESSING)))
                .thenReturn(listOf(task1, task2))
            whenever(mockWorkerClient.getJobStatus("job-1"))
                .thenReturn(Result.success(MockJobStatusResponse("job-1", "PROCESSING", null)))
            whenever(mockWorkerClient.getJobStatus("job-2"))
                .thenReturn(Result.success(MockJobStatusResponse("job-2", "PROCESSING", null)))

            // when
            jobStatusPoller.pollAllProcessingJobs()

            // then
            verify(mockWorkerClient).getJobStatus("job-1")
            verify(mockWorkerClient).getJobStatus("job-2")
        }

        @Test
        @DisplayName("mockJobId가 없는 작업은 폴링하지 않는다")
        fun shouldSkipTask_when_NoMockJobId() {
            // given
            val task = createProcessingTaskWithoutJobId("task-1")

            whenever(imageTaskRepository.findAllByStatusIn(listOf(TaskStatus.PROCESSING)))
                .thenReturn(listOf(task))

            // when
            jobStatusPoller.pollAllProcessingJobs()

            // then
            verify(mockWorkerClient, never()).getJobStatus(any())
        }
    }

    @Nested
    @DisplayName("pollJobStatus")
    inner class PollJobStatus {

        @Test
        @DisplayName("COMPLETED 수신 시 작업을 완료 처리한다.")
        fun shouldCompleteTask_when_responseIsCompleted() {
            // given
            val taskId = "test-task-id"
            val mockJobId = "test-mock-job-id"
            val result = "작업 완료"

            whenever(mockWorkerClient.getJobStatus(mockJobId))
                .thenReturn(Result.success(MockJobStatusResponse(mockJobId, "COMPLETED", result)))

            // when
            jobStatusPoller.pollJobStatus(taskId, mockJobId)

            // then
            verify(imageTaskService).completeTask(taskId, result)
            verify(imageTaskService, never()).failTask(any(), any())
        }

        @Test
        @DisplayName("FAILED 수신 시 작업을 실패 처리한다.")
        fun shouldFailTask_when_responseIsFailed() {
            // given
            val taskId = "test-task-id"
            val mockJobId = "test-mock-job-id"

            whenever(mockWorkerClient.getJobStatus(mockJobId))
                .thenReturn(Result.success(MockJobStatusResponse(mockJobId, "FAILED", null)))

            // when
            jobStatusPoller.pollJobStatus(taskId, mockJobId)

            // then
            verify(imageTaskService).failTask(eq(taskId), any())
            verify(imageTaskService, never()).completeTask(any(), any())
        }

        @Test
        @DisplayName("PROCESSING 수신 시 아무 작업도 하지 않는다")
        fun shouldDoNothing_when_responseIsProcessing() {
            // given
            val taskId = "test-task-id"
            val mockJobId = "test-mock-job-id"

            whenever(mockWorkerClient.getJobStatus(mockJobId))
                .thenReturn(Result.success(MockJobStatusResponse(mockJobId, "PROCESSING", null)))

            // when
            jobStatusPoller.pollJobStatus(taskId, mockJobId)

            // then
            verify(imageTaskService, never()).completeTask(any(), any())
            verify(imageTaskService, never()).failTask(any(), any())
        }

        @Test
        @DisplayName("Mock Worker 호출 실패 시 예외를 로깅하고 계속 진행한다")
        fun shouldContinue_when_mockWorkerFails() {
            // given
            val taskId = "test-task-id"
            val mockJobId = "test-mock-job-id"

            whenever(mockWorkerClient.getJobStatus(mockJobId))
                .thenReturn(Result.failure(MockWorkerClient.MockWorkerException("Connection failed")))

            // when
            jobStatusPoller.pollJobStatus(taskId, mockJobId)

            // then
            verify(imageTaskService, never()).completeTask(any(), any())
            verify(imageTaskService, never()).failTask(any(), any())
        }
    }

    private fun createProcessingTask(taskId: String, mockJobId: String): ImageTask {
        return ImageTask(
            taskId = taskId,
            imageUrl = "https://example.com/image.jpg",
            idempotencyKey = "key-$taskId",
            status = TaskStatus.PROCESSING,
            mockJobId = mockJobId
        )
    }

    private fun createProcessingTaskWithoutJobId(taskId: String): ImageTask {
        return ImageTask(
            taskId = taskId,
            imageUrl = "https://example.com/image.jpg",
            idempotencyKey = "key-$taskId",
            status = TaskStatus.PROCESSING,
            mockJobId = null
        )
    }
}