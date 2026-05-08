package com.tinkermode.aggregation.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient

/**
 * Holds all changeable properties and read from application.yaml or env vars.
 */
@Configuration
class AppProperties(
    @Value($$"${app.transform-svc-url}") val transformSvcUrl: String,
    @Value($$"${app.storage-svc-url}")   val storageSvcUrl: String,
    @Value($$"${app.transform-workers}") val transformWorkers: Int,
    @Value($$"${app.ingest-queue-capacity}") val ingestQueueCapacity: Int,
    @Value($$"${app.storage-queue-capacity}") val storageQueueCapacity: Int,
    @Value($$"${app.batch-size}")        val batchSize: Int,
    @Value($$"${app.batch-flush-ms}")    val batchFlushMs: Long,
)

@Configuration
class AppConfig {

    @Bean("transformRestClient")
    fun transformRestClient(props: AppProperties): RestClient =
        RestClient.builder()
            .baseUrl(props.transformSvcUrl)
            .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
            .build()


    @Bean("storageRestClient")
    fun storageRestClient(props: AppProperties): RestClient =
        RestClient.builder()
            .baseUrl(props.storageSvcUrl)
            .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
            .build()
}

