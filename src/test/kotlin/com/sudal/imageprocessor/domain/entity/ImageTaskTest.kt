package com.sudal.imageprocessor.domain.entity

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

@DisplayName("ImageTask 테스트")
class ImageTaskTest {

    @Nested
    @DisplayName("markAsProcessing")
    inner class MarkAsProcessing {

        @Test
        @DisplayName("PENDING -> PROCESSING 상태를 전이하고 mockJobId를 설정한다.")
        fun shouldTransitionToProcessingAndSetMockJobId() {
            // given
            val task = createPendingTask()
            val mockJobId = "mock-job-id"

            // when
            task.markAsProcessing(mockJobId)

            // then
            assertThat(task.status).isEqualTo(TaskStatus.PROCESSING)
            assertThat(task.mockJobId).isEqualTo(mockJobId)
        }

        @Test
        @DisplayName("PROCESSING -> PROCESSING 상태 전이 호출 시 예외를 던진다.")
        fun shouldThrowException_when_alreadyProcessing() {
            // given
            val task = createPendingTask()
            task.markAsProcessing("job-1")

            // when & then
            assertThatThrownBy { task.markAsProcessing("job-2") }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("유효하지 않은 상태 변경입니다")
        }
    }

    @Nested
    @DisplayName("markAsCompleted")
    inner class MarkAsCompleted {

        @Test
        @DisplayName("PROCESSING -> COMPLETED 상태를 전이하고 result를 설정한다.")
        fun shouldTransitionToCompletedAndSetResult() {
            // given
            val task = createProcessingTask()
            val result = "작업 완료"

            // when
            task.markAsCompleted(result)

            // then
            assertThat(task.status).isEqualTo(TaskStatus.COMPLETED)
            assertThat(task.result).isEqualTo(result)
        }

        @Test
        @DisplayName("PENDING -> COMPLETED 상태 전이 호출 시 예외를 던진다.")
        fun shouldThrowException_when_alreadyTransitionFromPending() {
            // given
            val task = createPendingTask()

            // when & then
            assertThatThrownBy { task.markAsCompleted("작업 완료") }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("유효하지 않은 상태 변경입니다")
        }
    }

    @Nested
    @DisplayName("markAsFailed")
    inner class MarkAsFailed {

        @Test
        @DisplayName("PENDING -> FAILED 상태를 전이하고 errorMessage를 설정한다.")
        fun shouldTransitionFromPendingToFailedAndSetErrorMessage() {
            // given
            val task = createPendingTask()
            val errorMessage = "작업 실패"

            // when
            task.markAsFailed(errorMessage)

            // then
            assertThat(task.status).isEqualTo(TaskStatus.FAILED)
            assertThat(task.errorMessage).isEqualTo(errorMessage)
        }

        @Test
        @DisplayName("PROCESSING -> FAILED 상태를 전이하고 errorMessage를 설정한다.")
        fun shouldTransitionFromProcessingToFailedAndSetErrorMessage() {
            // given
            val task = createProcessingTask()
            val errorMessage = "작업 실패"

            // when
            task.markAsFailed(errorMessage)

            // then
            assertThat(task.status).isEqualTo(TaskStatus.FAILED)
            assertThat(task.errorMessage).isEqualTo(errorMessage)
        }

        @Test
        @DisplayName("COMPLETED -> FAILED 상태 전이 호출 시 예외를 던진다.")
        fun shouldThrowException_when_alreadyCompleted() {
            // given
            val task = createCompletedTask()

            // when & then
            assertThatThrownBy { task.markAsFailed("작업 실패") }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("유효하지 않은 상태 변경입니다")
        }
    }

    @Nested
    @DisplayName("재시도 관련")
    inner class RetryMethods {

        @Test
        @DisplayName("재시도 횟수를 1 증가시킨다.")
        fun shouldIncrementRetryCount() {
            // given
            val task = createPendingTask()
            assertThat(task.retryCount).isZero

            // when
            task.incrementRetryCount()

            // then
            assertThat(task.retryCount).isEqualTo(1)
        }

        @Test
        @DisplayName("canRetry는 retryCount < MAX_RETRY_COUNT일 때 true를 반환한다.")
        fun shouldReturnTrue_when_retryCountUnderMaxRetryCount() {
            // given
            val task = createPendingTask()

            // when & then
            repeat(ImageTask.MAX_RETRY_COUNT) {
                assertThat(task.canRetry()).isTrue()
                task.incrementRetryCount()
            }

            assertThat(task.canRetry()).isFalse()
        }

        @Test
        @DisplayName("canRetry는 retryCount >= MAX_RETRY_COUNT일 때 false를 반환한다.")
        fun shouldReturnFalse_when_retryCountOverMaxRetryCount() {
            // given
            val task = createPendingTask()
            repeat(ImageTask.MAX_RETRY_COUNT) {
                task.incrementRetryCount()
            }

            // when
            val canRetry = task.incrementRetryCount()

            // then
            assertThat(canRetry).isFalse()
            assertThat(task.retryCount).isEqualTo(ImageTask.MAX_RETRY_COUNT + 1)
        }
    }


    private fun createPendingTask(): ImageTask {
        return ImageTask(
            imageUrl = "https://www.example.com/image.jpg",
            idempotencyKey = "test-key-${System.nanoTime()}"
        )
    }

    private fun createProcessingTask(): ImageTask {
        val task = createPendingTask()
        task.markAsProcessing("mock-job-id")
        return task
    }

    private fun createCompletedTask(): ImageTask {
        val task = createProcessingTask()
        task.markAsCompleted("작업 완료")
        return task
    }
}

@DisplayName("TaskStatus 테스트")
class TaskStatusTest {

    @Nested
    @DisplayName("canTransitionTo")
    inner class CanTransitionTo {

        @ParameterizedTest(name = "{0} -> {1} 전이가 가능해야한다.")
        @MethodSource("com.sudal.imageprocessor.domain.entity.TaskStatusTest#validTransitions")
        @DisplayName("허용되는 상태 전이")
        fun shouldAllowValidTransition(from: TaskStatus, to: TaskStatus) {
            assertThat(from.canTransitionTo(to)).isTrue()
        }

        @ParameterizedTest(name = "{0} -> {1} 전이는 불가능하다.")
        @MethodSource("com.sudal.imageprocessor.domain.entity.TaskStatusTest#invalidTransitions")
        @DisplayName("허용되지 않는 상태 전이")
        fun shouldNotAllowInvalidTransitions(from: TaskStatus, to: TaskStatus) {
            assertThat(from.canTransitionTo(to)).isFalse()
        }

        @Test
        @DisplayName("COMPLETED는 최종 상태이므로 어떤 상태로도 전이 불가")
        fun completedShouldBeFinalState() {
            TaskStatus.entries.forEach { status ->
                assertThat(TaskStatus.COMPLETED.canTransitionTo(status)).isFalse()
            }
        }

        @Test
        @DisplayName("FAILED는 최종 상태이므로 어떤 상태로도 전이 불가")
        fun failedShouldBeFinalState() {
            TaskStatus.entries.forEach { status ->
                assertThat(TaskStatus.FAILED.canTransitionTo(status)).isFalse()
            }
        }
    }

    companion object {

        @JvmStatic
        fun validTransitions(): Stream<Arguments> = Stream.of(
            Arguments.of(TaskStatus.PENDING, TaskStatus.PROCESSING),
            Arguments.of(TaskStatus.PENDING, TaskStatus.FAILED),
            Arguments.of(TaskStatus.PROCESSING, TaskStatus.COMPLETED),
            Arguments.of(TaskStatus.PROCESSING, TaskStatus.FAILED),
        )

        @JvmStatic
        fun invalidTransitions(): Stream<Arguments> = Stream.of(
            // PENDING -> COMPLETED 직접 전이 불가
            Arguments.of(TaskStatus.PENDING, TaskStatus.COMPLETED),
            // 역방향 전이 불가
            Arguments.of(TaskStatus.PROCESSING, TaskStatus.PENDING),
            Arguments.of(TaskStatus.COMPLETED, TaskStatus.PROCESSING),
            Arguments.of(TaskStatus.COMPLETED, TaskStatus.PENDING),
            Arguments.of(TaskStatus.FAILED, TaskStatus.PENDING),
            Arguments.of(TaskStatus.FAILED, TaskStatus.PROCESSING),
            // 완료 상태 전이 불가
            Arguments.of(TaskStatus.FAILED, TaskStatus.COMPLETED),
            Arguments.of(TaskStatus.COMPLETED, TaskStatus.FAILED),
            // 자기 자신으로 전이 불가
            Arguments.of(TaskStatus.PENDING, TaskStatus.PENDING),
            Arguments.of(TaskStatus.PROCESSING, TaskStatus.PROCESSING),
            Arguments.of(TaskStatus.COMPLETED, TaskStatus.COMPLETED),
            Arguments.of(TaskStatus.FAILED, TaskStatus.FAILED),
        )
    }
}