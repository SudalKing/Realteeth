package com.sudal.imageprocessor.domain.repository

import com.sudal.imageprocessor.domain.entity.ImageTask
import com.sudal.imageprocessor.domain.entity.TaskStatus
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime
import java.util.*

interface ImageTaskRepository : JpaRepository<ImageTask, Long> {

    fun findByTaskId(taskId: String): Optional<ImageTask>

    fun findByIdempotencyKey(idempotencyKey: String): Optional<ImageTask>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT it
        FROM ImageTask it
        WHERE it.taskId = :taskId
    """)
    fun findByTaskIdWithLock(@Param("taskId") taskId: String): Optional<ImageTask>

    @Query("""
        SELECT it
        FROM ImageTask it
        WHERE it.status = :status
        AND it.updatedAt < :before
        AND it.retryCount < :maxRetryCount
        ORDER BY it.createdAt
    """)
    fun findStuckTasks(
        @Param("status") status: TaskStatus,
        @Param("before") before: LocalDateTime,
        @Param("maxRetryCount") maxRetryCount: Int
    ): List<ImageTask>

    fun findAllByStatusIn(statuses: List<TaskStatus>): List<ImageTask>
}