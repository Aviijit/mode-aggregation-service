package com.tinkermode.aggregation.model

import com.fasterxml.jackson.annotation.JsonProperty


/**
 * Inbound - from Edge Device Simulator - API:  POST /data
 */
data class DevicePayload(
    @JsonProperty("device_id")
    val deviceId: String?,

    @JsonProperty("timestamp")
    val timestamp: String?,

    @JsonProperty("payload")
    val payload: Map<String, Any>?
)


/**
 * Transform Service - API: POST /transform
 */
data class TransformRequest(
    @JsonProperty("device_id")
    val deviceId: String,

    @JsonProperty("timestamp")
    val timestamp: String,

    @JsonProperty("payload")
    val payload: Map<String, Any>
)

data class TransformResponse(
    val transformed: Map<String, Any>
)

/**
 *  Internal - record queued for storage after successful transform
 */
data class StorageRecord(
    @JsonProperty("device_id")
    val deviceId: String,

    @JsonProperty("timestamp")
    val timestamp: String,

    @JsonProperty("data")
    val data: Map<String, Any>
)

/**
 *  Storage Service - API:  POST /write
 *  Body is a plain JSON array of StorageRecord
 */
data class WriteResponse(
    val stored: Int
)

/**
 * API Response
 */
data class AcceptedResponse(
    val status: String = "accepted"
)

data class ErrorResponse(
    val status: String = "error",
    val message: String,
)