package com.realteeth.assignment.infrastructure.scheduler

import com.realteeth.assignment.application.service.JobStatusPoller
import com.realteeth.assignment.application.service.OutboxProcessor
import com.realteeth.assignment.domain.entity.Outbox
import com.realteeth.assignment.domain.entity.OutboxStatus
import com.realteeth.assignment.domain.entity.TaskStatus
import com.realteeth.assignment.domain.repository.ImageTaskRepository
import com.realteeth.assignment.domain.repository.OutboxRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class OutboxScheduler(
    private val outboxRepository: OutboxRepository,
    private val outboxProcessor: OutboxProcessor
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 초기 딜레기 5초, 이후 10초마다 PENDING 상태인 Outbox Event 처리
     * - Spring Event 처리 실패 시 백업 역할
     * - 서버 재시작 후 미처리 이벤트 복구 역할
     */
    @Scheduled(fixedDelay = 10_000, initialDelay = 5_000)
    fun processOutboxEvents() {
        log.debug("[Outbox] behavior: Outbox Event 처리 배치 | START | message: Outbox Event 처리 배치 시작")

        try {
            val stuckEvents = outboxRepository.findStuckEvents(
                status = OutboxStatus.PENDING,
                before = LocalDateTime.now().minusSeconds(Outbox.RETRY_INTERVAL_SECOND),
                maxRetryCount = Outbox.MAX_RETRY_COUNT
            )

            if (stuckEvents.isNotEmpty()) {
                log.info("[Outbox] behavior: Outbox Event 처리 배치 | START | message: Outbox Event 처리 대상 ${stuckEvents.size}건")

                stuckEvents.forEach { outbox ->
                    try {
                        log.info("[Outbox] behavior: Outbox Event 처리 배치 | PROCESSING | outboxId: ${outbox.id} | message: Outbox Event ")
                        outboxProcessor.processOutboxEvent(outbox.id)
                    } catch (e: Exception) {
                        log.error("[Outbox] behavior: Outbox Event 처리 배치 | FAIL | outboxId: ${outbox.id} | message: Outbox Event 처리 실패", e)
                    }
                }
            }
        } catch (e: Exception) {
            log.error("[Outbox] behavior: Outbox Event 처리 배치 | FAIL | message: Outbox Event 처리 배치 실패", e)
        }
    }
}

@Component
class StuckRecoveryScheduler(
    private val imageTaskRepository: ImageTaskRepository,
    private val outboxRepository: OutboxRepository,
    private val outboxProcessor: OutboxProcessor
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 1분마다 PENDING 상태에서 오래 멈춘 작업 복구
     * - Outbox Event 누락 혹은 처리 실패한 경우
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    fun recoverStuckTasks() {
        log.debug("[Outbox] behavior: Stuck Outbox Event 복구 배치 | START | message: Stuck Outbox Event 복구 배치 시작")

        try {
            val stuckTasks = imageTaskRepository.findStuckTasks(
                status = TaskStatus.PENDING,
                before = LocalDateTime.now().minusMinutes(2),
                maxRetryCount = 3
            )

            if (stuckTasks.isNotEmpty()) {
                log.info("[Outbox] behavior: Stuck Outbox Event 복구 배치 | START | message: Stuck Outbox Event 복구 대상 ${stuckTasks.size}건")

                stuckTasks.forEach { task ->
                    val outboxList = outboxRepository.findByAggregateId(task.taskId)
                    val pendingOutbox = outboxList.firstOrNull{ it.status == OutboxStatus.PENDING }

                    pendingOutbox?.let {
                        log.info("[Outbox] behavior: Stuck Outbox Event 복구 배치 | PROCESSING | taskId: ${task.taskId} | message: Stuck Outbox Event 재시도")
                        outboxProcessor.processOutboxEvent(it.id)
                    }
                }
            }
        } catch (e: Exception) {
            log.error("[Outbox] behavior: Stuck Outbox Event 복구 배치 | FAIL | message: Stuck Outbox Event 복구 배치 실패", e)
        }
    }
}

@Component
class JobPollingScheduler(
    private val jobStatusPoller: JobStatusPoller
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 5초마다 PROCESSING 상태인 작업의 상태 폴링
     */
    @Scheduled(fixedDelay = 5_000, initialDelay = 5_000)
    fun pollingJobStatus() {
        log.debug("[ImageTask] behavior: 처리 중인 작업 조회 | START | message: 처리 중인 작업 조회 시작")

        try {
            jobStatusPoller.pollAllProcessingJobs()
        } catch (e: Exception) {
            log.error("[ImageTask] behavior: 처리 중인 작업 조회 | FAIL | message: 처리 중인 작업 조회 실패", e)
        }
    }
}