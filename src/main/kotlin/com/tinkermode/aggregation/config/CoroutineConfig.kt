package com.tinkermode.aggregation.config

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class CoroutineConfig {

    /**
     * Application-wide coroutine scope backed by a SupervisorJob.
     *
     * SupervisorJob ensures that a failure in one child coroutine (e.g. a
     * worker crashing after retries are exhausted) does NOT cancel the entire
     * scope and bring down all other workers.
     *
     * The scope is canceled in AggregationServiceApplication.onDestroy()
     * during graceful shutdown.
     */
    @Bean
    fun appCoroutineScope(): CoroutineScope = CoroutineScope(SupervisorJob())
}
