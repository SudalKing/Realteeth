package com.sudal.imageprocessor

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.retry.annotation.EnableRetry
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling

@EnableScheduling
@EnableAsync
@EnableRetry
@SpringBootApplication
class ImageProcessorApplication

fun main(args: Array<String>) {
    runApplication<com.sudal.imageprocessor.ImageProcessorApplication>(*args)
}
