package com.sudal.imageprocessor.domain.entity

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("Outbox 테스트")
class OutboxTest {

    @Nested
    @DisplayName("createTaskCreatedEvent")
    inner class CreateTaskCreatedEvent {

        @Test
        @DisplayName("Outbox를 생성한다.")
        fun shouldCreateOutbox() {
            // given
            val taskId = "test-task-id"
            val payload = """
                {
                    "taskId": "$taskId",
                    "imageUrl": "https://example.com/image.jpg"
                }
            """.trimIndent()

            // when
            val outbox = Outbox.createTaskCreatedEvent(taskId, payload)

            // then
            assertThat(outbox.aggregateType).isEqualTo("ImageTask")
            assertThat(outbox.aggregateId).isEqualTo(taskId)
            assertThat(outbox.eventType).isEqualTo("TASK_CREATED")
            assertThat(outbox.payload).isEqualTo(payload)
            assertThat(outbox.status).isEqualTo(OutboxStatus.PENDING)
            assertThat(outbox.retryCount).isZero
            assertThat(outbox.processedAt).isNull()
        }
    }

    @Nested
    @DisplayName("markAsProcessed")
    inner class MarkAsProcessed {

        @Test
        @DisplayName("PENDING -> PROCESSED 상태를 변경하고 processedAt을 설정한다.")
        fun shouldUpdateProcessedAndSetProcessedAt() {
            // given
            val outbox = createPendingOutbox()
            assertThat(outbox.processedAt).isNull()

            // when
            outbox.markAsProcessed()

            // then
            assertThat(outbox.status).isEqualTo(OutboxStatus.PROCESSED)
            assertThat(outbox.processedAt).isNotNull()
        }
    }

    @Nested
    @DisplayName("markAsFailed")
    inner class MarkAsFailed {

        @Test
        @DisplayName("PENDING/PROCESSED -> FAILED 상태를 변경하고 processedAt을 설정한다")
        fun shouldUpdateFailedAndSetProcessedAt() {
            // given
            val outbox = createPendingOutbox()

            // when
            outbox.markAsFailed()

            // then
            assertThat(outbox.status).isEqualTo(OutboxStatus.FAILED)
            assertThat(outbox.processedAt).isNotNull()
        }
    }

    @Nested
    @DisplayName("재시도 관련")
    inner class RetryMethods {

        @Test
        @DisplayName("재시도 횟수를 1 증가시킨다")
        fun shouldIncrementRetryCount() {
            // given
            val outbox = createPendingOutbox()
            assertThat(outbox.retryCount).isZero

            // when
            outbox.incrementRetryCount()

            // then
            assertThat(outbox.retryCount).isEqualTo(1)
        }

        @Test
        @DisplayName("canRetry는 retryCount < MAX_RETRY_COUNT일 때 true를 반환한다.")
        fun shouldReturnTrue_when_retryCountUnderMaxRetryCount() {
            // given
            val outbox = createPendingOutbox()

            // when & then
            repeat(Outbox.MAX_RETRY_COUNT) {
                assertThat(outbox.canRetry()).isTrue()
                outbox.incrementRetryCount()
            }

            assertThat(outbox.canRetry()).isFalse()
        }

        @Test
        @DisplayName("canRetry는 retryCount >= MAX_RETRY_COUNT일 때 false를 반환한다.")
        fun shouldReturnFalse_when_retryCountOverMaxRetryCount() {
            // given
            val outbox = createPendingOutbox()
            repeat(Outbox.MAX_RETRY_COUNT) {
                outbox.incrementRetryCount()
            }

            // when
            val result = outbox.incrementRetryCount()

            // then
            assertThat(result).isFalse()
            assertThat(outbox.retryCount).isEqualTo(Outbox.MAX_RETRY_COUNT + 1)
        }

        @Test
        @DisplayName("canRetry는 PENDING 상태이고 재시도 횟수가 최대 미만일 때 true를 반환한다")
        fun canRetryShouldReturnTrue_whenPendingAndBelowMaxRetry() {
            // given
            val outbox = createPendingOutbox()

            // when & then
            assertThat(outbox.canRetry()).isTrue()
        }

        @Test
        @DisplayName("PENDING 상태가 아니라면 false를 반환한다")
        fun shouldReturnFalse_when_StateIsNotPending() {
            // given
            val outbox = createPendingOutbox()
            outbox.markAsProcessed()

            // when & then
            assertThat(outbox.canRetry()).isFalse()
        }
    }

    private fun createPendingOutbox(): Outbox {
        return Outbox.createTaskCreatedEvent(
            taskId = "test-task-id",
            payload = """
                {
                    "taskId": "test-task-id",
                    "imageUrl": "https://example.com/image.jpg"
                }
            """
        )
    }
}