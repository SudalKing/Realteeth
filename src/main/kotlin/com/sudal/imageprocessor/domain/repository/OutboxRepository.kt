package com.sudal.imageprocessor.domain.repository

import com.sudal.imageprocessor.domain.entity.Outbox
import com.sudal.imageprocessor.domain.entity.OutboxStatus
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface OutboxRepository : JpaRepository<Outbox, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT ob
        FROM Outbox ob
        WHERE ob.status = :status
        ORDER BY ob.createdAt
        LIMIT :limit
    """)
    fun findPendingEventsWithLock(
        @Param("status") status: OutboxStatus = OutboxStatus.PENDING,
        @Param("limit") limit: Int = 100
    ) : List<Outbox>

    @Query("""
        SELECT ob
        FROM Outbox ob
        WHERE ob.status = :status
        AND ob.createdAt < :before
        AND ob.retryCount < :maxRetryCount
        ORDER BY ob.createdAt
    """)
    fun findStuckEvents(
        @Param("status") status: OutboxStatus = OutboxStatus.PENDING,
        @Param("before") before: LocalDateTime,
        @Param("maxRetryCount") maxRetryCount: Int
    ) : List<Outbox>

    fun findByAggregateId(aggregateId: String): List<Outbox>
}