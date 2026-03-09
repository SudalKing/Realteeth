package com.realteeth.assignment.presentation

import com.fasterxml.jackson.databind.ObjectMapper
import com.realteeth.assignment.domain.repository.ImageTaskRepository
import com.realteeth.assignment.domain.repository.OutboxRepository
import com.realteeth.assignment.infrastructure.client.MockWorkerClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("ImageTaskController 통합 테스트")
class ImageTaskControllerIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var imageTaskRepository: ImageTaskRepository

    @Autowired
    private lateinit var outboxRepository: OutboxRepository

    @MockBean
    private lateinit var mockWorkerClient: MockWorkerClient

    @BeforeEach
    fun setUp() {
        outboxRepository.deleteAll()
        imageTaskRepository.deleteAll()
    }

    @AfterEach
    fun tearDown() {
        outboxRepository.deleteAll()
        imageTaskRepository.deleteAll()
    }

    @Nested
    @DisplayName("[1.1] 이미지 처리 작업 요청 - POST /api/v1/tasks")
    inner class CreateTask {

        @Test
        @DisplayName("새로운 작업 요청 시 202 Accepted를 반환한다.")
        fun shouldReturn202Accepted_when_requestIsNew() {
            // given
            val request = """
                {
                    "imageUrl": "https://example.com/image.jpg",
                    "idempotencyKey": "test-key"
                }
            """.trimIndent()

            // when & then
            mockMvc.perform(
                post("/api/v1/tasks")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request)
            )
                .andDo(print())
                .andExpect(status().isAccepted)
                .andExpect(jsonPath("$.taskId").exists())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.message").exists())

            // DB 저장 확인
            val tasks = imageTaskRepository.findAll()
            assertThat(tasks).hasSize(1)
            assertThat(tasks[0].imageUrl).isEqualTo("https://example.com/image.jpg")
            assertThat(tasks[0].idempotencyKey).isEqualTo("test-key")
        }

        @Test
        @DisplayName("중복 요청 시 기존 작업을 반환한다.")
        fun shouldReturnExistingTask_when_requestIsDuplicated() {
            // given
            // when
            // then
        }

        @Test
        @DisplayName("imageUrl이 없으면 400 Bad Request를 반환한다.")
        fun shouldReturn400BadRequest_when_imageUrlIsNone() {
            // given
            // when
            // then
        }

        @Test
        @DisplayName("idempotencyKey가 없으면 400 Bad Request를 반환한다.")
        fun shouldReturn400BadRequest_when_idempotencyKeyIsNone() {
            // given
            // when
            // then
        }
    }

    @Test
    @DisplayName("imageUrl이 빈 값이면 400 Bad Request를 반환한다.")
    fun shouldReturn400BadRequest_when_imageUrlIsBlank() {
        // given
        // when
        // then
    }

    @Test
    @DisplayName("idempotencyKey가 빈 값이면 400 Bad Request를 반환한다.")
    fun shouldReturn400BadRequest_when_idempotencyKeyIsBlank() {
        // given
        // when
        // then
    }

    @Nested
    @DisplayName("[1.2] 이미지 처리 작업 상태 조회 - GET /api/v1/tasks/{taskId}")
    inner class GetTask {

        @Test
        @DisplayName("taskId로 조회 시 200 OK와 작업 정보를 반환한다.")
        fun shouldReturn200OK_when_taskExists() {
            // given
            // when
            // then
        }

        @Test
        @DisplayName("존재하지 않는 taskId로 조회 시 404 Not Found를 반환한다.")
        fun shouldReturn404NotFound_when_taskNotFound() {
            // given
            // when
            // then
        }
    }

    @Nested
    @DisplayName("[1.3] 모든 이미지 처리 작업 목록 조회 - GET /api/v1/tasks")
    inner class GetAllTasks {

        @Test
        @DisplayName("목록 조회 시 200 OK와 작업 전체 목록을 반환한다.")
        fun shouldReturn200OKWithAllTasks() {
            // given
            // when
            // then
        }

        @Test
        @DisplayName("status 파라미터로 여러 상태를 필터링한다.")
        fun shouldReturnAllTasksInMultipleTasks() {
            // given
            // when
            // then
        }
    }
}