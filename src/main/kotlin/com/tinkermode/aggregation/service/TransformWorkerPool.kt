package com.tinkermode.aggregation.service


import com.tinkermode.aggregation.config.AppProperties
import com.tinkermode.aggregation.model.StorageRecord
import com.tinkermode.aggregation.model.TransformRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class TransformWorkerPool(
    private val transformClient: TransformClient,
    private val ingestService: IngestService,
    private val appScope: CoroutineScope,
    private val props: AppProperties,
) {
    private val log = LoggerFactory.getLogger(TransformWorkerPool::class.java)

    /**
     * Transformed records flow into this channel for the StorageBatcher to consume.
     */
    val storageChannel: Channel<StorageRecord> =
        Channel(capacity = props.storageQueueCapacity)


    /**
     * Starts [props.transformWorkers] coroutines, each pulling from
     * [IngestService.ingestChannel] and pushing results to [storageChannel].
     *
     * Called once at application startup (from AggregationServiceApplication).
     */
    fun start() {
        log.info("Starting ${props.transformWorkers} transform workers")
        repeat(props.transformWorkers) { workerId ->
            appScope.launch {
                for (request in ingestService.ingestChannel) {
                    process(workerId, request)
                }
                // Channel closed -> this worker exits cleanly (graceful shutdown)
            }
        }
    }


    private suspend fun process(workerId: Int, request: TransformRequest) {
        try {
            val response = transformClient.transform(request)
            val record = StorageRecord(
                deviceId = request.deviceId,
                timestamp = request.timestamp,
                data = response.transformed
            )
            storageChannel.send(record)  // suspend if storage channel is full
        } catch (e: Exception) {
            // After all retries are exhausted the record is unrecoverable.
            // Log with enough detail to reconstruct the loss, then move on.
            // A single worker failure must not crash the pool (SupervisorJob handles this).
            log.error(
                "Worker[$workerId] failed to transform device=${request.deviceId} " +
                        "ts=${request.timestamp} - record dropped after retries: ${e.message}"
            )
        }
    }
}