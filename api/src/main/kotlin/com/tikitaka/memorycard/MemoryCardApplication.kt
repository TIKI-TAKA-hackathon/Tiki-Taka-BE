package com.tikitaka.memorycard

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class MemoryCardApplication

fun main(args: Array<String>) {
    runApplication<MemoryCardApplication>(*args)
}
