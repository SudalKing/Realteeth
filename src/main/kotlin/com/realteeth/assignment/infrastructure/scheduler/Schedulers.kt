package com.realteeth.assignment.infrastructure.scheduler

import com.realteeth.assignment.application.service.OutboxProcessor
import com.realteeth.assignment.domain.entity.Outbox
import com.realteeth.assignment.domain.entity.OutboxStatus
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
                log.info("Found ${stuckEvents.size} stuck events")
                stuckEvents.forEach { outbox ->
                    try {
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
}
