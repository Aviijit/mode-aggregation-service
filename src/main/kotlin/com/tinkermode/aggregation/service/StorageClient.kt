package com.tinkermode.aggregation.service

import com.tinkermode.aggregation.model.StorageRecord
import com.tinkermode.aggregation.model.WriteResponse
import com.tinkermode.aggregation.util.HttpException
import com.tinkermode.aggregation.util.RateLimitException
import com.tinkermode.aggregation.util.retryWithBackoff
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.client.toEntity

@Component
class StorageClient(private val storageRestClient: RestClient) {

    private val log = LoggerFactory.getLogger(StorageClient::class.java)

    /**
     * Writes a batch of records to API:  POST /write.
     * Retries entire batch on 5xx. Respects Retry-After on 429.
     * Returns the number of stored records on success.
     * Throws after exhausted retries - caller must not silently drop records.
     */
    suspend fun write(records: List<StorageRecord>): Int {
        require(records.isNotEmpty()) { "Batch records must not be empty" }
        require(records.size <= 100) { "Batch records size must not exceed 100" }

        return retryWithBackoff(
            maxAttempts = 6,
            initialDelayMs = 100,
            maxDelayMs = 10_000,
            operationName = "storage.write[batch=${records.size}]"
        ) {
            try {
                val response = storageRestClient
                    .post()
                    .uri("/write")
                    .body(records)
                    .retrieve()
                    .toEntity<WriteResponse>()

                response.body?.stored
                    ?: throw IllegalStateException("Storage service returned empty body")
            } catch (e: RestClientResponseException) {
                val status = e.statusCode.value()
                if (status == 429) {
                    // Parse Retry-After header (seconds integer or fallback)
                    val retryAfterSec = e.responseHeaders?.get("Retry-After")
                        ?.firstOrNull()
                        ?.toLongOrNull()
                        ?: 1L
                    throw RateLimitException(retryAfterSec * 1_000, "Storage 429: rate limited")
                }
                throw HttpException(status, "Storage $status: ${e.responseBodyAsString}")
            }
        }
    }
}