package com.realteeth.assignment.infrastructure.client

import com.realteeth.assignment.application.dto.MockJobStatusResponse
import com.realteeth.assignment.application.dto.MockProcessRequest
import com.realteeth.assignment.application.dto.MockProcessResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.exchange
import org.springframework.web.client.postForEntity

@Component
class MockWorkerClient(
    private val restTemplate: RestTemplate,
    @param:Value("\${mock-worker.base-url}") private val baseUrl: String,
    @param:Value("\${mock-worker.api-key}") private val apiKey: String
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun submitJob(imageUrl: String): Result<MockProcessResponse> {
        return try {
            val headers = createHeaders()
            val request = HttpEntity(MockProcessRequest(imageUrl), headers)
            val response = restTemplate.postForEntity<MockProcessResponse>(
                "$baseUrl/process",
                request
            )

            when {
                response.statusCode.is2xxSuccessful && response.body != null -> {
                    log.info("[ImageTask] behavior: 작업 요청 | SUCCESS | response: ${response.body} | message: Mock Worker 작업 요청")
                    Result.success(response.body!!)
                }
                response.statusCode.is2xxSuccessful && response.body == null -> {
                    log.error("[ImageTask] behavior: 작업 요청 | FAIL | status: ${response.statusCode} | message: Mock Worker 응답 body null")
                    Result.failure(MockWorkerException("Mock Worker 서버의 응답이 null입니다.: ${response.statusCode}"))
                }
                response.statusCode.is4xxClientError -> {
                    log.error("[ImageTask] behavior: 작업 요청 | FAIL | status: ${response.statusCode} | message: Mock Worker 작업 요청 에러")
                    Result.failure(MockWorkerException("Mock Worker 서버 작업 요청에 실패했습니다.: ${response.statusCode}"))
                }
                else -> {
                    log.error("[ImageTask] behavior: 작업 요청 | FAIL | status: ${response.statusCode} | message: Mock Worker 서버 에러")
                    Result.failure(MockWorkerException("Mock Worker 서버에서 오류가 발생했습니다.: ${response.statusCode}"))
                }
            }
        } catch (e: RestClientException) {
            log.error("[ImageTask] behavior: 작업 요청 | FAIL | message: Mock Worker 통신 실패")
            Result.failure(MockWorkerException("Mock Worker 서버와 통신에 실패했습니다.: ${e.message}"))
        }
    }

    fun getJobStatus(jobId: String): Result<MockJobStatusResponse> {
        return try {
            val headers = createHeaders()
            val request = HttpEntity<Void>(headers)
            val response = restTemplate.exchange<MockJobStatusResponse>(
                "$baseUrl/process/$jobId",
                HttpMethod.GET,
                request
            )

            if (response.statusCode.is2xxSuccessful && response.body != null) {
                log.info("[ImageTask] behavior: 작업 상태 조회 | SUCCESS | response: ${response.body} | message: Mock Worker 작업 상태 조회")
                Result.success(response.body!!)
            } else {
                log.error("ImageTask] behavior: 작업 상태 조회 | FAIL | status: ${response.statusCode} | message: Mock Worker 작업 상태 조회 실패")
                Result.failure(MockWorkerException("Mock Worker 서버 작업 상태 확인에 실패했습니다.: ${response.statusCode}"))
            }
        } catch (e: RestClientException) {
            log.error("[ImageTask] behavior: 작업 상태 조회 | FAIL | message: Mock Worker 통신 실패")
            Result.failure(MockWorkerException("Mock Worker 서버와 통신에 실패했습니다.: ${e.message}"))
        }
    }

    private fun createHeaders(): HttpHeaders {
        return HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            set("X-API-KEY", apiKey)
        }
    }

    class MockWorkerException(message: String, cause: Throwable? = null) : RuntimeException(message)
}