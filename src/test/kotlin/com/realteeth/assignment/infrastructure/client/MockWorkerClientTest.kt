package com.realteeth.assignment.infrastructure.client

import com.realteeth.assignment.application.dto.MockJobStatusResponse
import com.realteeth.assignment.application.dto.MockProcessResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestTemplate

@ExtendWith(MockitoExtension::class)
@DisplayName("MockWorkerClient 테스트")
class MockWorkerClientTest {

    @Mock
    private lateinit var restTemplate: RestTemplate

    private lateinit var mockWorkerClient: MockWorkerClient

    private val baseUrl = "https://dev.realteeth,ai/mock"
    private val apiKey = "mock_b64d77a3625b4dd099387479769f557e"

    @BeforeEach
    fun setUp() {
        mockWorkerClient = MockWorkerClient(restTemplate, baseUrl, apiKey)
    }

    @Nested
    @DisplayName("submitJob")
    inner class SubmitJob {

        @Test
        @DisplayName("Mock Worker 서버에 작업을 제출하면 success를 반환한다.")
        fun shouldReturnSuccess_when_JobSubmittedSuccessfully() {
            // given
            val imageUrl = "https://example.com/image.jpg"
            val expectedResponse = MockProcessResponse(
                jobId = "test-job-id",
                status = "PROCESSING"
            )

            whenever(restTemplate.postForEntity(
                eq("$baseUrl/process"),
                any<HttpEntity<*>>(),
                eq(MockProcessResponse::class.java)
            )).thenReturn(ResponseEntity.ok(expectedResponse))

            // when
            val result = mockWorkerClient.submitJob(imageUrl)

            // then
            assertThat(result.isSuccess).isTrue()
            assertThat(result.getOrNull()?.jobId).isEqualTo("test-job-id")
            assertThat(result.getOrNull()?.status).isEqualTo("PROCESSING")
            verify(restTemplate).postForEntity(
                eq("$baseUrl/process"),
                argThat<HttpEntity<*>> { entity ->
                    entity.headers["X-API-KEY"]?.contains(apiKey) == true
                },
                eq(MockProcessResponse::class.java)
            )
        }

        @Test
        @DisplayName("Mock Worker 서버가 4xx/5xx 응답 시 failure를 반환한다.")
        fun shouldReturnFailure_when_JobSubmittedFailed() {
            // given
            val imageUrl = "https://example.com/image.jpg"

            whenever(restTemplate.postForEntity(
                eq("$baseUrl/process"),
                any<HttpEntity<*>>(),
                eq(MockProcessResponse::class.java)
            )).thenReturn(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build())

            // when
            val result = mockWorkerClient.submitJob(imageUrl)

            // then
            assertThat(result.isFailure).isTrue()
            assertThat(result.exceptionOrNull())
                .isInstanceOf(MockWorkerClient.MockWorkerException::class.java)
        }

        @Test
        @DisplayName("Mock Worker 서버와 통신 중 네트워크 예외 발생 시 failure를 반환한다")
        fun shouldReturnFailure_when_NetworkError() {
            // given
            val imageUrl = "https://example.com/image.jpg"

            whenever(restTemplate.postForEntity(
                eq("$baseUrl/process"),
                any<HttpEntity<*>>(),
                eq(MockProcessResponse::class.java)
            )).thenThrow(RestClientException("Connection refused"))

            // when
            val result = mockWorkerClient.submitJob(imageUrl)

            // then
            assertThat(result.isFailure).isTrue()
            assertThat(result.exceptionOrNull())
                .isInstanceOf(MockWorkerClient.MockWorkerException::class.java)
                .hasMessageContaining("Connection refused")
        }

        @Test
        @DisplayName("Mock Worker 서버의 응답 body가 null이면 failure를 반환한다")
        fun shouldReturnFailure_when_BodyIsNull() {
            // given
            val imageUrl = "https://example.com/image.jpg"

            whenever(restTemplate.postForEntity(
                eq("$baseUrl/process"),
                any<HttpEntity<*>>(),
                eq(MockProcessResponse::class.java)
            )).thenReturn(ResponseEntity.ok(null))

            // when
            val result = mockWorkerClient.submitJob(imageUrl)

            // then
            assertThat(result.isFailure).isTrue()
        }
    }

    @Nested
    @DisplayName("getJobStatus")
    inner class GetJobStatus {

        @Test
        @DisplayName("작업 상태 조회에 성공하면 success를 반환한다.")
        fun shouldReturnSuccess_when_GetJobStatusSuccessfully() {
            // given
            val jobId = "test-job-id"
            val expectedResponse = MockJobStatusResponse(
                jobId = jobId,
                status = "COMPLETED",
                result = "작업 완료"
            )

            whenever(restTemplate.exchange(
                eq("$baseUrl/process/$jobId"),
                eq(HttpMethod.GET),
                any<HttpEntity<*>>(),
                any<ParameterizedTypeReference<MockJobStatusResponse>>()
            )).thenReturn(ResponseEntity.ok(expectedResponse))

            // when
            val result = mockWorkerClient.getJobStatus(jobId)

            // then
            assertThat(result.isSuccess).isTrue()
            assertThat(result.getOrNull()?.jobId).isEqualTo(jobId)
            assertThat(result.getOrNull()?.status).isEqualTo("COMPLETED")
            assertThat(result.getOrNull()?.result).isEqualTo("작업 완료")
        }

        @Test
        @DisplayName("Mock Worker 서버와 조회 통신 중 네트워크 예외 발생 시 failure를 반환한다")
        fun shouldReturnFailure_when_NetworkError() {
            // given
            val jobId = "test-job-id"

            whenever(restTemplate.exchange(
                eq("$baseUrl/process/$jobId"),
                eq(HttpMethod.GET),
                any<HttpEntity<*>>(),
                any<ParameterizedTypeReference<MockJobStatusResponse>>()
            )).thenThrow(RestClientException("Read timeout"))

            // when
            val result = mockWorkerClient.getJobStatus(jobId)

            // then
            assertThat(result.isFailure).isTrue()
            assertThat(result.exceptionOrNull())
                .isInstanceOf(MockWorkerClient.MockWorkerException::class.java)
                .hasMessageContaining("Read timeout")
        }
    }
}