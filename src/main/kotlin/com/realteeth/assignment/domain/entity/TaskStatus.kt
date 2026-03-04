package com.realteeth.assignment.domain.entity

enum class TaskStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED
    ;

    /**
     * STATE MACHINE 정의
     * 1. PENDING -> PROCESSING or FAILED
     * 2. PROCESSING -> COMPLETED or FAILED
     * 3. COMPLETED -> X
     * 4. FAILED -> X
     */
    fun canTransitionTo(next: TaskStatus): Boolean {
        return when (this) {
            PENDING -> next in listOf(PROCESSING, FAILED)
            PROCESSING -> next in listOf(COMPLETED, FAILED)
            COMPLETED -> false
            FAILED -> false
        }
    }
}