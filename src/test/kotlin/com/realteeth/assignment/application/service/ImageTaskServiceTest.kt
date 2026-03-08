package com.realteeth.assignment.application.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.realteeth.assignment.application.dto.CreateTaskRequest
import com.realteeth.assignment.application.service.impl.ImageTaskServiceImpl
import com.realteeth.assignment.domain.entity.ImageTask
import com.realteeth.assignment.domain.entity.Outbox
import com.realteeth.assignment.domain.entity.TaskStatus
import com.realteeth.assignment.domain.event.TaskCreatedEvent
import com.realteeth.assignment.domain.repository.ImageTaskRepository
import com.realteeth.assignment.domain.repository.OutboxRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import org.springframework.context.ApplicationEventPublisher
import java.util.*

@ExtendWith(MockitoExtension::class)
@DisplayName("ImageTaskService 테스트")
class ImageTaskServiceTest {

    @Mock
    private lateinit var imageTaskRepository: ImageTaskRepository

    @Mock
    private lateinit var outboxRepository: OutboxRepository

    @Mock
    private lateinit var eventPublisher: ApplicationEventPublisher

    @Captor
    private lateinit var taskCaptor: ArgumentCaptor<ImageTask>

    private lateinit var objectMapper: ObjectMapper
    private lateinit var imageTaskService: ImageTaskServiceImpl

    @BeforeEach
    fun setUp() {
        objectMapper = ObjectMapper()
        imageTaskService = ImageTaskServiceImpl(
            imageTaskRepository,
            outboxRepository,
            objectMapper,
            eventPublisher,
        )
    }

    @Nested
    @DisplayName("createImageTask")
    inner class CreateTask {

        @Test
        @DisplayName("새로운 idempotencyKey로 요청 시 Task를 생성한다.")
        fun shouldCreateNewTask_when_IdempotencyKeyIsNew() {
            // given
            val request = CreateTaskRequest(
                imageUrl = "https://example.com/image.jpg",
                idempotencyKey = "unique-key-123"
            )

            whenever(imageTaskRepository.findByIdempotencyKey(request.idempotencyKey))
                .thenReturn(Optional.empty())
            whenever(imageTaskRepository.save(any<ImageTask>()))
                .thenAnswer { it.arguments[0] as ImageTask }
            whenever(outboxRepository.save(any<Outbox>()))
                .thenAnswer { it.arguments[0] as Outbox }

            // when
            val response = imageTaskService.createImageTask(request)

            // then
            assertThat(response.taskId).isNotBlank()
            assertThat(response.status).isEqualTo(TaskStatus.PENDING)
            assertThat(response.message).isEqualTo("작업이 생성되었습니다.")

            verify(imageTaskRepository).save(taskCaptor.capture())
            val savedTask = taskCaptor.value
            assertThat(savedTask.imageUrl).isEqualTo(request.imageUrl)
            assertThat(savedTask.idempotencyKey).isEqualTo(request.idempotencyKey)

            verify(outboxRepository).save(any<Outbox>())
            verify(eventPublisher).publishEvent(argThat<TaskCreatedEvent> { event ->
                event.taskId.isNotBlank() && event.imageUrl == request.imageUrl
            })
        }

        @Test
        @DisplayName("중복된 idempotencyKey로 요청 시 기존 작업을 반환한다.")
        fun shouldReturnExistingTask_when_IdempotencyKeyIsDuplicate() {
            // given
            val request = CreateTaskRequest(
                imageUrl = "https://example.com/image.jpg",
                idempotencyKey = "duplicate-key"
            )

            val existingTask = ImageTask(
                taskId = "existing-task-id",
                imageUrl = request.imageUrl,
                idempotencyKey = request.idempotencyKey,
                status = TaskStatus.PROCESSING
            )

            whenever(imageTaskRepository.findByIdempotencyKey(request.idempotencyKey))
                .thenReturn(Optional.of(existingTask))

            // when
            val response = imageTaskService.createImageTask(request)

            // then
            assertThat(response.taskId).isEqualTo("existing-task-id")
            assertThat(response.status).isEqualTo(TaskStatus.PROCESSING)
            assertThat(response.message).isEqualTo("작업이 중복 요청되어 기존 작업을 반환합니다.")

            verify(imageTaskRepository, never()).save(any<ImageTask>())
            verify(outboxRepository, never()).save(any<Outbox>())
        }
    }

    @Nested
    @DisplayName("getTask")
    inner class GetTask {

        @Test
        @DisplayName("존재하는 작업 조회 시 응답을 반환한다.")
        fun shouldReturnResponse_when_TaskExists() {
            // given
            val taskId = "test-task-id"
            val task = ImageTask(
                taskId = taskId,
                imageUrl = "https://example.com/image.jpg",
                idempotencyKey = "test-key-123",
                status = TaskStatus.COMPLETED,
                result = "작업 완료"
            )

            whenever(imageTaskRepository.findByTaskId(taskId))
                .thenReturn(Optional.of(task))

            // when
            val response = imageTaskService.getTask(taskId)

            // then
            assertThat(response.taskId).isEqualTo(taskId)
            assertThat(response.status).isEqualTo(TaskStatus.COMPLETED)
            assertThat(response.result).isEqualTo(task.result)
            assertThat(response.createdAt).isEqualTo(task.createdAt)
        }


        @Test
        @DisplayName("존재하지 않는 작업 조회 시 TaskNotFoundException을 던진다")
        fun shouldThrowException_when_TaskNotFound() {
            // given
            val taskId = "none-existing-task-id"

            whenever(imageTaskRepository.findByTaskId(taskId))
                .thenReturn(Optional.empty())

            // when & then
            assertThatThrownBy { imageTaskService.getTask(taskId) }
                .isInstanceOf(ImageTaskServiceImpl.TaskNotFoundException::class.java)
                .hasMessageContaining(taskId)
        }
    }

    @Nested
    @DisplayName("updateTaskToProcessing")
    inner class UpdateTaskToProcessing {

        @Test
        @DisplayName("작업 상태를 변경한다.(PENDING -> PROCESSING)")
        fun shouldUpdateToProcessing_when_TaskIsPending() {
            // given
            val taskId = "test-task-id"
            val mockJobId = "mock-job-id"
            val task = ImageTask(
                taskId = taskId,
                imageUrl = "https://example.com/image.jpg",
                idempotencyKey = "key-123",
                status = TaskStatus.PENDING
            )

            whenever(imageTaskRepository.findByTaskIdWithLock(taskId))
                .thenReturn(Optional.of(task))
            whenever(imageTaskRepository.save(any<ImageTask>()))
                .thenAnswer { it.arguments[0] }

            // when
            imageTaskService.updateTaskToProcessing(taskId, mockJobId)

            // then
            verify(imageTaskRepository).save(taskCaptor.capture())
            val updatedTask = taskCaptor.value
            assertThat(updatedTask.taskId).isEqualTo(taskId)
            assertThat(updatedTask.status).isEqualTo(TaskStatus.PROCESSING)
            assertThat(updatedTask.mockJobId).isEqualTo(mockJobId)
        }

        @Test
        @DisplayName("PENDING이 아닌 작업은 상태를 변경하지 않는다.")
        fun shouldNotUpdate_when_TaskIsNotPending() {
            // given
            val taskId = "test-task-id"
            val mockJobId = "mock-job-id"
            val task = ImageTask(
                taskId = taskId,
                imageUrl = "https://example.com/image.jpg",
                idempotencyKey = "key-123",
                status = TaskStatus.PROCESSING
            )

            whenever(imageTaskRepository.findByTaskIdWithLock(taskId))
                .thenReturn(Optional.of(task))

            // when
            imageTaskService.updateTaskToProcessing(taskId, mockJobId)

            // then
            verify(imageTaskRepository, never()).save(any<ImageTask>())
        }
    }

    @Nested
    @DisplayName("completeTask")
    inner class CompleteTask {

        @Test
        @DisplayName("작업 상태를 변경한다.(PROCESSING -> COMPLETED)")
        fun shouldUpdateToCompleted_when_TaskIsProcessing() {
            // given
            val taskId = "test-task-id"
            val result = "처리 완료"
            val task = ImageTask(
                taskId = taskId,
                imageUrl = "https://example.com/image.jpg",
                idempotencyKey = "key-123",
                status = TaskStatus.PROCESSING
            )

            whenever(imageTaskRepository.findByTaskIdWithLock(taskId))
                .thenReturn(Optional.of(task))
            whenever(imageTaskRepository.save(any<ImageTask>()))
                .thenAnswer { it.arguments[0] }

            // when
            imageTaskService.completeTask(taskId, result)

            // then
            verify(imageTaskRepository).save(taskCaptor.capture())
            val updatedTask = taskCaptor.value
            assertThat(updatedTask.taskId).isEqualTo(taskId)
            assertThat(updatedTask.status).isEqualTo(TaskStatus.COMPLETED)
            assertThat(updatedTask.result).isEqualTo(result)
        }

        @Test
        @DisplayName("PROCESSING이 아닌 작업은 상태를 변경하지 않는다.")
        fun shouldNotUpdate_when_TaskIsNotProcessing() {
            // given
            val taskId = "test-task-id"
            val result = "NONE"
            val task = ImageTask(
                taskId = taskId,
                imageUrl = "https://example.com/image.jpg",
                idempotencyKey = "key-123",
                status = TaskStatus.PENDING
            )

            whenever(imageTaskRepository.findByTaskIdWithLock(taskId))
                .thenReturn(Optional.of(task))

            // when
            imageTaskService.completeTask(taskId, result)

            // then
            verify(imageTaskRepository, never()).save(any<ImageTask>())
        }

        @Test
        @DisplayName("존재하지 않는 작업 상태를 변경하려할 시 TaskNotFoundException 예외를 던진다.")
        fun shouldThrowException_when_TaskNotFound() {
            // given
            val taskId = "none-existing-task-id"
            val result = "FAIL"

            whenever(imageTaskRepository.findByTaskIdWithLock(taskId))
                .thenReturn(Optional.empty())

            // when & then
            assertThatThrownBy { imageTaskService.completeTask(taskId, result) }
                .isInstanceOf(ImageTaskServiceImpl.TaskNotFoundException::class.java)
                .hasMessageContaining(taskId)
        }
    }

    @Nested
    @DisplayName("failTask")
    inner class FailTask {

        @Test
        @DisplayName("작업 상태를 변경한다.(PENDING/PROCESSING -> FAILED")
        fun shouldUpdateToFailed_when_TaskIsInPendingOrProcessing() {
            // given
            val taskId = "test-task-id"
            val errorMessage = "작업 실패"
            val task = ImageTask(
                taskId = taskId,
                imageUrl = "https://example.com/image.jpg",
                idempotencyKey = "key-123",
                status = TaskStatus.PENDING // PENDING or PROCESSING
            )

            whenever(imageTaskRepository.findByTaskIdWithLock(taskId))
                .thenReturn(Optional.of(task))
            whenever(imageTaskRepository.save(any<ImageTask>()))
                .thenAnswer { it.arguments[0] }

            // when
            imageTaskService.failTask(taskId, errorMessage)

            // then
            verify(imageTaskRepository).save(taskCaptor.capture())
            val updatedTask = taskCaptor.value
            assertThat(updatedTask.taskId).isEqualTo(taskId)
            assertThat(updatedTask.status).isEqualTo(TaskStatus.FAILED)
            assertThat(updatedTask.errorMessage).isEqualTo(errorMessage)
        }

        @Test
        @DisplayName("PENDING/PROCESSING이 아닌 작업은 상태를 변경하지 않는다.")
        fun shouldNotUpdate_when_TaskIsNotInPendingOrProcessing() {
            // given
            val taskId = "test-task-id"
            val errorMessage = "작업 실패"
            val task = ImageTask(
                taskId = taskId,
                imageUrl = "https://example.com/image.jpg",
                idempotencyKey = "key-123",
                status = TaskStatus.COMPLETED
            )

            whenever(imageTaskRepository.findByTaskIdWithLock(taskId))
                .thenReturn(Optional.of(task))

            // when
            imageTaskService.failTask(taskId, errorMessage)

            // then
            verify(imageTaskRepository, never()).save(any<ImageTask>())
        }

        @Test
        @DisplayName("존재하지 않는 작업 상태를 변경하려할 시 TaskNotFoundException 예외를 던진다.")
        fun shouldThrowException_when_TaskNotFound() {
            // given
            val taskId = "test-task-id"
            val errorMessage = "작업 실패"

            whenever(imageTaskRepository.findByTaskIdWithLock(taskId))
                .thenReturn(Optional.empty())

            // when & then
            assertThatThrownBy { imageTaskService.failTask(taskId, errorMessage) }
                .isInstanceOf(ImageTaskServiceImpl.TaskNotFoundException::class.java)
                .hasMessageContaining("작업을 찾을 수 없습니다")
        }
    }

    @Nested
    @DisplayName("incrementRetryAndCheck")
    inner class IncrementRetryAndCheck {

        @Test
        @DisplayName("재시도 횟수를 증가시키고 재시도 가능 여부를 반환한다.")
        fun shouldIncrementRetryCount() {
            // given
            val taskId = "test-task-id"
            val task = ImageTask(
                taskId = taskId,
                imageUrl = "https://example.com/image.jpg",
                idempotencyKey = "key-123",
                retryCount = 0
            )

            whenever(imageTaskRepository.findByTaskIdWithLock(taskId))
                .thenReturn(Optional.of(task))
            whenever(imageTaskRepository.save(any<ImageTask>()))
                .thenAnswer { it.arguments[0] }

            // when
            val canRetry = imageTaskService.incrementRetryAndCheck(taskId)

            // then
            assertThat(canRetry).isTrue
            verify(imageTaskRepository).save(taskCaptor.capture())
            assertThat(taskCaptor.value.retryCount).isEqualTo(1)
        }

        @Test
        @DisplayName("재시도 횟수 초과 시 FAILED로 변경하고 false를 반환한다.")
        fun shouldUpdateToFailed_when_MaxRetryExceeded() {
            // given
            val taskId = "test-task-id"
            val task = ImageTask(
                taskId = taskId,
                imageUrl = "https://example.com/image.jpg",
                idempotencyKey = "key-123",
                retryCount = ImageTask.MAX_RETRY_COUNT
            )

            whenever(imageTaskRepository.findByTaskIdWithLock(taskId))
                .thenReturn(Optional.of(task))
            whenever(imageTaskRepository.save(any<ImageTask>()))
                .thenAnswer { it.arguments[0] }

            // when
            val canRetry = imageTaskService.incrementRetryAndCheck(taskId)

            // then
            assertThat(canRetry).isFalse
            verify(imageTaskRepository, times(2)).save(any())
        }
    }
}