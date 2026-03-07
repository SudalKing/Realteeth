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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
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
    @DisplayName("CreateTask 메서드")
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

    }

}