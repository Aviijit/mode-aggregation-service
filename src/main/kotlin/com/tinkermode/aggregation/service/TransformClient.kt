package com.tinkermode.aggregation.service

import com.tinkermode.aggregation.model.TransformRequest
import com.tinkermode.aggregation.model.TransformResponse
import com.tinkermode.aggregation.util.HttpException
import com.tinkermode.aggregation.util.retryWithBackoff
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.client.body


@Component
class TransformClient(
    @Qualifier("transformRestClient") private val transformRestClient: RestClient,
) {
    private val log = LoggerFactory.getLogger(TransformClient::class.java)


    /**
     * Call API: POST /transform with bounded retry on 5xx.
     * Return the transformed map on success.
     * Throws after all retries are exhausted.
     */

    suspend fun transform(request: TransformRequest): TransformResponse =
        retryWithBackoff(
            maxAttempts = 5,
            initialDelayMs = 200,
            maxDelayMs = 8_000,
            operationName = "transform[${request.deviceId}]"
        ) {
            try {
                transformRestClient
                    .post()
                    .uri("/transform")
                    .body(request)
                    .retrieve()
                    .body<TransformResponse>()
                    ?: throw IllegalStateException("Transform service returned empty body")
            } catch (e: RestClientResponseException) {
                throw HttpException(e.statusCode.value(), "Transform ${e.statusCode}: ${e.responseBodyAsString}")
            }
        }

}