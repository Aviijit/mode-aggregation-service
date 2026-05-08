package com.tinkermode.aggregation.service

import com.tinkermode.aggregation.config.AppProperties
import com.tinkermode.aggregation.model.StorageRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import kotlin.time.Duration.Companion.milliseconds

@Component
class StorageBatcher(
    private val storageClient: StorageClient,
    private val workerPool: TransformWorkerPool,
    private val appScope: CoroutineScope,
    private val props: AppProperties,
) {
    private val log = LoggerFactory.getLogger(StorageBatcher::class.java)

    /**
     * A plain Channel used as a tick signal — avoids the ObsoleteCoroutinesApi
     * ticker. A separate coroutine sends Unit every props.batchFlushMs ms.
     */
    private val tickChannel = Channel<Unit>(capacity = Channel.CONFLATED)

    /**
     * Starts a single coroutine that owns the batch buffer exclusively.
     * No shared mutable state - only this coroutine ever touches batch.
     *
     * Flushes when:
     *  - batch reaches props.batchSize (max 100)
     *  - props.batchFlushMs elapses since last flush (via tickChannel)
     */
    fun start() {
        log.info("Starting storage batcher (batchSize=${props.batchSize}, flushMs=${props.batchFlushMs})")

        // Timer coroutine - sends a tick every batchFlushMs
        appScope.launch {
            while (true) {
                delay(props.batchFlushMs.milliseconds)
                tickChannel.trySend(Unit) // CONFLATED: drops tick if one is already waiting
            }
        }

        // Batcher coroutine - owns the batch exclusively
        appScope.launch {
            val batch = mutableListOf<StorageRecord>()
            var running = true

            try {
                while (running) {
                    select {
                        workerPool.storageChannel.onReceiveCatching { result ->
                            val record = result.getOrNull()
                            if (record != null) {
                                batch.add(record)
                                if (batch.size >= props.batchSize) {
                                    flush(batch)
                                }
                            } else {
                                // Channel was closed (graceful shutdown) -- flush and exit loop
                                if (batch.isNotEmpty()) {
                                    log.info("Storage channel closed, flushing final batch of ${batch.size}")
                                    flush(batch)
                                }
                                running = false   // exits the while(running) loop cleanly
                            }
                        }
                        tickChannel.onReceive {
                            if (batch.isNotEmpty()) {
                                flush(batch)
                            }
                        }
                    }
                }
            } finally {
                // flush anything remaining on unexpected coroutine exit
                if (batch.isNotEmpty()) {
                    log.warn("Batcher exiting with ${batch.size} unflushed records — attempting final flush")
                    runCatching { flush(batch) }
                        .onFailure { log.error("Final flush failed: ${it.message}") }
                }
            }
        }
    }


    private suspend fun flush(batch: MutableList<StorageRecord>) {
        val snapshot = batch.toList()
        batch.clear()
        try {
            val stored = storageClient.write(snapshot)
            log.info("Stored $stored/${snapshot.size} records")
        } catch (e: Exception) {
            // After all retries, log with full detail - nothing will be dropped silently.
            log.error(
                "Failed to write batch of ${snapshot.size} records after retries: ${e.message}. " +
                        "Device IDs: ${snapshot.map { it.deviceId }}"
            )
        }
    }
}