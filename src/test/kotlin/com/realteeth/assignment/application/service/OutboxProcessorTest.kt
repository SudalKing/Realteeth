package com.realteeth.assignment.application.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.realteeth.assignment.application.dto.MockJobStatusResponse
import com.realteeth.assignment.application.dto.MockProcessResponse
import com.realteeth.assignment.application.dto.TaskCreatedPayload
import com.realteeth.assignment.domain.entity.Outbox
import com.realteeth.assignment.domain.entity.OutboxStatus
import com.realteeth.assignment.domain.repository.OutboxRepository
import com.realteeth.assignment.infrastructure.client.MockWorkerClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.*

@ExtendWith(MockitoExtension::class)
@DisplayName("OutboxProcessor 테스트")
class OutboxProcessorTest {

    @Mock
    private lateinit var outboxRepository: OutboxRepository

    @Mock
    private lateinit var imageTaskService: ImageTaskService

    @Captor
    private lateinit var outboxCaptor: ArgumentCaptor<Outbox>

    private lateinit var fakeMockWorkerClient: FakeMockWorkerClient
    private lateinit var objectMapper: ObjectMapper
    private lateinit var outboxProcessor: OutboxProcessor

    @BeforeEach
    fun setUp() {
        objectMapper = ObjectMapper().registerKotlinModule()
        fakeMockWorkerClient = FakeMockWorkerClient()

        outboxProcessor = OutboxProcessor(
            outboxRepository,
            fakeMockWorkerClient,
            imageTaskService,
            objectMapper
        )
    }

    @Nested
    @DisplayName("processOutboxEvent")
    inner class ProcessOutboxEvent {

        @Test
        @DisplayName("Mock Worker 서버 호출 성공 시 Outbox를 PROCESSED로 변경한다.")
        fun shouldMarkAsProcessed_when_MockWorkerSucceeds() {
            // given
            val taskId = "test-task-id"
            val payload = TaskCreatedPayload(taskId, "http://example.com/image.jpg")
            val outbox = Outbox.createTaskCreatedEvent(
                taskId = taskId,
                payload = objectMapper.writeValueAsString(payload)
            ).apply {
                val idField = Outbox::class.java.getDeclaredField("id")
                idField.isAccessible = true
                idField.set(this, 1L)
            }

            fakeMockWorkerClient.submitJobResponse = Result.success(
                MockProcessResponse(
                    jobId = "test-job-id",
                    status = "PROCESSING"
                )
            )

            whenever(outboxRepository.findById(1L))
                .thenReturn(Optional.of(outbox))
            whenever(outboxRepository.save(any<Outbox>()))
                .thenAnswer { it.arguments[0] }

            // when
            outboxProcessor.processOutboxEvent(1L)

            // then
            verify(imageTaskService).updateTaskToProcessing(taskId, "test-job-id")
            verify(outboxRepository).save(outboxCaptor.capture())
            assertThat(outboxCaptor.value.status).isEqualTo(OutboxStatus.PROCESSED)
        }

        @Test
        @DisplayName("Mock Worker 서버 호출 실패 시 재시도 카운트를 증가시킨다.")
        fun shouldIncrementRetryCount_when_MockWorkerFails() {
            // given
            val taskId = "test-task-id"
            val payload = TaskCreatedPayload(taskId, "https://example.com/image.jpg")
            val outbox = Outbox.createTaskCreatedEvent(
                taskId = taskId,
                payload = objectMapper.writeValueAsString(payload)
            ).apply {
                val idField = Outbox::class.java.getDeclaredField("id")
                idField.isAccessible = true
                idField.set(this, 1L)
            }

            whenever(outboxRepository.findById(1L))
                .thenReturn(Optional.of(outbox))
            fakeMockWorkerClient.submitJobResponse = Result.failure(
                MockWorkerClient.MockWorkerException("Connection failed")
            )
            whenever(outboxRepository.save(any<Outbox>())).thenAnswer { it.arguments[0] }

            // when
            outboxProcessor.processOutboxEvent(1L)

            // then
            verify(imageTaskService, never()).updateTaskToProcessing(any(), any())
            verify(outboxRepository).save(outboxCaptor.capture())
            assertThat(outboxCaptor.value.retryCount).isEqualTo(1)
            assertThat(outboxCaptor.value.status).isEqualTo(OutboxStatus.PENDING)
        }

        @Test
        @DisplayName("이미 처리된 Outbox는 스킵한다.")
        fun shouldSkip_when_alreadyProcessed() {
            // given
            val taskId = "test-task-id"
            val payload = TaskCreatedPayload(taskId, "https://example.com/image.jpg")
            val outbox = Outbox.createTaskCreatedEvent(
                taskId = taskId,
                payload = objectMapper.writeValueAsString(payload)
            ).apply {
                val idField = Outbox::class.java.getDeclaredField("id")
                idField.isAccessible = true
                idField.set(this, 1L)
                markAsProcessed()
            }

            whenever(outboxRepository.findById(1L))
                .thenReturn(Optional.of(outbox))

            // when
            outboxProcessor.processOutboxEvent(1L)

            // then
            verify(imageTaskService, never()).updateTaskToProcessing(any(), any())
        }
    }


    // Fake 구현체
    class FakeMockWorkerClient : MockWorkerClient(
        restTemplate = mock(),
        baseUrl = "http://fake",
        apiKey = "fake-key"
    ) {
        var submitJobResponse: Result<MockProcessResponse> = Result.failure(Exception("Not set"))
        var getJobStatusResponse: Result<MockJobStatusResponse> = Result.failure(Exception("Not set"))

        override fun submitJob(imageUrl: String): Result<MockProcessResponse> {
            return submitJobResponse
        }

        override fun getJobStatus(jobId: String): Result<MockJobStatusResponse> {
            return getJobStatusResponse
        }
    }
}