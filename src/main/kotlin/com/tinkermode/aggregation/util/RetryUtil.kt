package com.tinkermode.aggregation.util

import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import kotlin.math.min
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds


private val log = LoggerFactory.getLogger("RetryUtil")

/**
 *  HTTP call failed with a status code the caller should
 *  inspect to decide whether to retry.
 */
class HttpException(val statusCode: Int, message: String) : RuntimeException(message)



/**
 *  429 was received. [retryAfterMs] is derived from the
 *  Retry-After response header (defaults to fallbackDelayMs when absent).
 */
class RateLimitException(val retryAfterMs: Long, message: String) : RuntimeException(message)


/**
 *
 * Executes [block] up to [maxAttempts] times, applying exponential backoff
 * with ±25 % jitter between retries.
 *
 * Retry rules:
 *  - 5xx HttpException -> retry with backoff
 *  - RateLimitException -> delay exactly retryAfterMs then retry
 *  - 4xx HttpException -> rethrow immediately (non-retryable)
 *  - Any other exception -> rethrow immediately
 *
 * After [maxAttempts] exhausted the last exception is rethrown.
 *
 */

suspend fun <T> retryWithBackoff(
    maxAttempts: Int = 5,
    initialDelayMs: Long = 200,
    maxDelayMs: Long = 8_000,
    operationName: String = "operation",
    block: suspend () -> T
): T {
    var delayMs = initialDelayMs
    repeat(maxAttempts) {   attempt ->
        try {
            return block()
        } catch (e: RateLimitException) {
            if (attempt == maxAttempts - 1) throw e
            log.warn("[$operationName] 429 rate-limited, waiting ${e.retryAfterMs}ms (attempt ${attempt + 1}/$maxAttempts)")
//            delay(e.retryAfterMs.milliseconds)
            delay(e.retryAfterMs.milliseconds)
        }catch (e: HttpException) {
            if (e.statusCode < 500) throw e  // 4xx - non-retryable ex. 400 < 500
            if(attempt == maxAttempts - 1) throw e // exhausted
            val jitteredDelay = (delayMs * Random.nextDouble(0.75, 1.25)).toLong()
            log.warn("[$operationName] ${e.statusCode} error, retrying in ${jitteredDelay}ms (attempt ${attempt + 1}/$maxAttempts)")
            delay(jitteredDelay.milliseconds)
            delayMs = min(delayMs * 2, maxDelayMs)
        }

    }
    // Unreachable - last iteration always throws or returns
    throw IllegalStateException("retryWithBackoff: exhausted without result")

}