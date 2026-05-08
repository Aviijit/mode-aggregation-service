package com.tinkermode.aggregation.web

import com.tinkermode.aggregation.model.AcceptedResponse
import com.tinkermode.aggregation.model.DevicePayload
import com.tinkermode.aggregation.model.ErrorResponse
import com.tinkermode.aggregation.service.IngestResult
import com.tinkermode.aggregation.service.IngestService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.annotation.*

@RestController
class DataController(private val ingestService: IngestService) {

    private val log = LoggerFactory.getLogger(DataController::class.java)

    /**
     * Accepts an edge device payload.
     *
     * Responses:
     *  202 Accepted - payload is valid and queued for processing
     *  400 Bad Request - payload is malformed or fails validation
     *  503 Service Unavailable - ingest queue is full; edge will retry
     */
    @PostMapping("/data", consumes = ["application/json"])
    fun ingest(@RequestBody payload: DevicePayload): ResponseEntity<Any> {
        return when (val result = ingestService.enqueue(payload)) {
            is IngestResult.Accepted -> {
                ResponseEntity.status(HttpStatus.ACCEPTED).body(AcceptedResponse())
            }
            is IngestResult.Invalid -> {
                log.warn("Rejected invalid payload from device=${payload.deviceId}: ${result.reason}")
                ResponseEntity.badRequest().body(ErrorResponse(message = result.reason))
            }
            is IngestResult.QueueFull -> {
                log.warn("Ingest queue full — returning 503 to trigger edge retry")
                ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .header("Retry-After", "1")
                    .body(ErrorResponse(message = "Service temporarily unavailable, please retry"))
            }
        }
    }

    /**
     * Handles completely unparseable JSON bodies before they reach [ingest].
     */
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleMalformedJson(e: HttpMessageNotReadableException): ResponseEntity<ErrorResponse> {
        log.warn("Malformed JSON body: ${e.message}")
        return ResponseEntity.badRequest()
            .body(ErrorResponse(message = "Malformed JSON: ${e.mostSpecificCause.message}"))
    }
}