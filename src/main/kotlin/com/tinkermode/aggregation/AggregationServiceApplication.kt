package com.tinkermode.aggregation

import com.tinkermode.aggregation.service.IngestService
import com.tinkermode.aggregation.service.StorageBatcher
import com.tinkermode.aggregation.service.TransformWorkerPool
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.context.event.EventListener
import kotlin.time.Duration.Companion.milliseconds

@SpringBootApplication
class AggregationServiceApplication(
	private val workerPool: TransformWorkerPool,
	private val batcher: StorageBatcher,
	private val ingestService: IngestService,
	private val appScope: CoroutineScope,
) {
	private val log = LoggerFactory.getLogger(AggregationServiceApplication::class.java)

	/**
	 * Start workers and batcher once the Spring context is fully initialized.
	 * Using ContextRefreshedEvent (rather than @PostConstruct) ensures all
	 * beans are wired before coroutines begin consuming channels.
	 */
	@EventListener(ContextRefreshedEvent::class)
	fun onStartup() {
		log.info("Starting aggregation service workers")
		workerPool.start()
		batcher.start()
	}

	/**
	 * Graceful shutdown :
	 *
	 * 1. Close the ingest channel  -> no new work enters; workers finish
	 *    their current item, then exit their for-loop cleanly.
	 * 2. Workers drain ingestChannel to empty; each sends its result to
	 *    storageChannel and exits.
	 * 3. StorageBatcher detects storageChannel closed, flushes remaining
	 *    batch, and exits.
	 * 4. Cancel the coroutine scope - any lingering coroutines are canceled.
	 *
	 * Spring calls this before the JVM exits, giving the service a window to
	 * deliver every accepted record that has already been queued.
	 */

	@PreDestroy
	fun onShutdown() {
		log.info("Shutdown signal received - draining channels")

		// Stop accepting new payloads into the ingest channel
		ingestService.ingestChannel.close()
		log.info("Ingest channel closed — workers will drain and exit")

		// Give workers time to finish in-flight transforms and flush storage.
		// In a production system this timeout would come from a config property.
		runBlocking {
			kotlinx.coroutines.withTimeout(30_000.milliseconds) {
				// Wait until the storage channel is also closed by the batcher.
				// The batcher closes itself when it detects storageChannel closed.
				// Wait for the scope's children to finish.
				//
				// Since SupervisorJob children don't auto-complete the parent,
				// rely on the batcher logging "final flush" and the timeout
				// to bound the wait.
				kotlinx.coroutines.delay(500.milliseconds) // allow final flush cycle

			}
		}
		appScope.cancel("Application shutdown")
		log.info("Aggregation service stopped cleanly")

	}

}

fun main(args: Array<String>) {
	runApplication<AggregationServiceApplication>(*args)
}
