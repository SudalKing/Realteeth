package com.realteeth.assignment.application.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.realteeth.assignment.application.dto.TaskCreatedPayload
import com.realteeth.assignment.domain.entity.Outbox
import com.realteeth.assignment.domain.entity.OutboxStatus
import com.realteeth.assignment.domain.repository.OutboxRepository
import com.realteeth.assignment.infrastructure.client.MockWorkerClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class OutboxProcessor(
    private val outboxRepository: OutboxRepository,
    private val mockWorkerClient: MockWorkerClient,
    private val imageTaskService: ImageTaskService,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Outbox Event 처리
     * - Mock Worker에 작업 요청
     * - 성공: 작업 상태 변경(PENDING -> PROCESSING)
     * - 실패: 재시도 횟수 증가 -> 최대 재시도 횟수 초과 시 실패 처리(PENDING -> FAILED)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun processOutboxEvent(outboxId: Long) {
        val outbox = outboxRepository.findById(outboxId).orElse(null) ?: run {
            log.warn("[Outbox] behavior: Outbox Event 처리 | NONE | outboxId: $outboxId | message: 조회되는 Outbox가 없습니다.")
            return
        }

        if (outbox.status != OutboxStatus.PENDING) {
            log.debug("[Outbox] behavior: Outbox Event 처리 | NONE | outboxId: {} | status: {} | message: 이미 처리되었습니다.", outboxId, outbox.status)
            return
        }

        try {
            val payload = objectMapper.readValue(outbox.payload, TaskCreatedPayload::class.java)
            log.info("[Outbox] behavior: Outbox Event 처리 | SUCCESS | outboxId: $outboxId | taskId: ${payload.taskId} | message: Outbox 처리를 요청합니다.")

            val result = mockWorkerClient.submitJob(payload.imageUrl)

            result.fold(
                onSuccess = { response ->
                    imageTaskService.updateTaskToProcessing(payload.taskId, response.jobId)
                    outbox.markAsProcessed()
                    outboxRepository.save(outbox)
                    log.info("[Outbox] behavior: Outbox Event 처리 | SUCCESS | outboxId: $outboxId | jobId: ${response.jobId} | status: ${response.status} | message: Outbox Event가 성공적으로 처리되었습니다.")
                },
                onFailure = { e ->
                    handleOutboxFailure(outbox, e.message ?: "알 수 없는 에러입니다.")
                }
            )
        } catch (e: Exception) {
            log.error("[Outbox] behavior: Outbox Event 처리 | FAIL | outboxId: $outboxId | message: Outbox Event 처리 중 에러가 발생했습니다.")
            handleOutboxFailure(outbox, e.message ?: "알 수 없는 에러입니다.")
        }
    }

    private fun handleOutboxFailure(outbox: Outbox, errorMessage: String) {
        if (!outbox.incrementRetryCount()) {
            // 재시도 초과
            outbox.markAsFailed()
            log.error("Outbox 재시도 횟수를 초과했습니다. : ${outbox.id}")

            // 작업 실패 처리
            try {
                val payload = objectMapper.readValue(outbox.payload, TaskCreatedPayload::class.java)
                imageTaskService.failTask(payload.taskId, "Outbox 재시도 횟수를 초과하여 Mock Worker 서버 요청에 실패했습니다. : $errorMessage")
            } catch (e: Exception) {
                log.error("작업 상태 변경에 실패했습니다. : $errorMessage", e)
            }
        }

        outboxRepository.save(outbox)
    }
}