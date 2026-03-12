package com.sudal.imageprocessor.application.service

import com.sudal.imageprocessor.domain.entity.OutboxStatus
import com.sudal.imageprocessor.domain.event.TaskCreatedEvent
import com.sudal.imageprocessor.domain.repository.OutboxRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class TaskEventListener(
    private val outboxRepository: OutboxRepository,
    private val outboxProcessor: OutboxProcessor
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 작업 요청 Outbox Event 처리
     * - AFTER_COMMIT: 트랜잭션 커밋 후 실행
     * - @Async: 비동기 처리로 응답 시간 지연 최소화
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleTaskCreatedEvent(event: TaskCreatedEvent) {

        log.info("[ImageTask] behavior: 작업 요청 이벤트 처리 | START | taskId: ${event.taskId} | message: 작업 요청 이벤트 처리 시작")

        try {
            val outboxList = outboxRepository.findByAggregateId(event.taskId)
            val pendingOutbox = outboxList.firstOrNull{ it.status == OutboxStatus.PENDING }

            pendingOutbox?.let {
                outboxProcessor.processOutboxEvent(it.id)
            } ?: run {
                log.warn("[ImageTask] behavior: 작업 요청 이벤트 처리 | NONE | taskId: ${event.taskId} | message: PENDING 상태 Outbox가 없음 ")
            }
        } catch (e: Exception) {
            log.error("[ImageTask] behavior: 작업 요청 이벤트 처리 | FAIL | taskId: ${event.taskId} | message: 작업 요청 이벤트 처리 실패", e)
        }
    }
}