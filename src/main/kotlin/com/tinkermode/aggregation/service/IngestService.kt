package com.tinkermode.aggregation.service

import com.tinkermode.aggregation.config.AppProperties
import com.tinkermode.aggregation.model.DevicePayload
import com.tinkermode.aggregation.model.TransformRequest
import kotlinx.coroutines.channels.Channel
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.format.DateTimeParseException


sealed class IngestResult {
    object Accepted  : IngestResult()
    object QueueFull : IngestResult()
    data class Invalid(val reason: String) : IngestResult()
}

@Service
class IngestService (
    props: AppProperties
) {


    /**
     * Unbounded-capacity would risk OOM; fixed capacity gives a natural
     * back-pressure signal (→ 503) when downstream is too slow.
     */
    val ingestChannel: Channel<TransformRequest> =
        Channel(capacity = props.ingestQueueCapacity)

    /**
     * Validates [payload] and tries to enqueue it for async processing.
     *
     * Returns:
     *  - [IngestResult.Accepted]  -> caller should respond 202
     *  - [IngestResult.Invalid]   -> caller should respond 400
     *  - [IngestResult.QueueFull] -> caller should respond 503
     */

    fun enqueue(payload: DevicePayload ): IngestResult {
        val validationError  = validate(payload)
        if (validationError != null) return IngestResult.Invalid(validationError)

        val request = TransformRequest(
            deviceId = payload.deviceId!!,
            timestamp = payload.timestamp!!,
            payload = payload.payload!!
        )

        // trySend is non-blocking; returns failure if channel is full
        return if (ingestChannel.trySend(request).isSuccess) {
            IngestResult.Accepted
        } else {
            IngestResult.QueueFull
        }

    }


    /**
     * Helper function to validate the quest payload
     */
    private fun validate(p: DevicePayload): String? {
        if (p.deviceId.isNullOrBlank())  return "device_id is missing or blank"
        if (p.timestamp.isNullOrBlank())  return "timestamp is missing or blank"
        if (p.payload == null)            return "payload is missing"

        // RFC 3339 / ISO-8601 timestamp check
        try {
            Instant.parse(p.timestamp)
        } catch (_: DateTimeParseException) {
            return "timestamp is not a valid RFC3339 value: '${p.timestamp}'"
        }

        return null
    }
}