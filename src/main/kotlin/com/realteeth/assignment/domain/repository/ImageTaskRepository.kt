package com.realteeth.assignment.domain.repository

import com.realteeth.assignment.domain.entity.ImageTask
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface ImageTaskRepository : JpaRepository<ImageTask, Long> {

    fun findByTaskId(taskId: String): Optional<ImageTask>


}