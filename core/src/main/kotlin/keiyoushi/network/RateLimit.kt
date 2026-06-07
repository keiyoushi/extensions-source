package keiyoushi.network

import android.os.SystemClock
import keiyoushi.utils.firstInstanceOrNull
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.util.ArrayDeque
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Adds an interceptor enforcing one or more rate limits.
 *
 * The first rule whose [shouldLimit] matches the request URL is applied;
 * remaining rules are skipped. Define more specific rules before broader ones:
 * ```
 * clientBuilder
 *     .rateLimit(5)  { it.host == "api.manga.example" }
 *     .rateLimit(20) { it.host == "img.manga.example" }
 * ```
 *
 * @param permits     Requests allowed per [period].
 * @param period      Sliding-window duration.
 * @param interval    Minimum gap between consecutive dispatches. Smooths bursts —
 *                    `permits=10, interval=100.ms` allows 10/s but spaced ≥100ms apart.
 * @param shouldLimit Whether this rule applies to a given URL.
 */
fun OkHttpClient.Builder.rateLimit(
    permits: Int,
    period: Duration = 1.seconds,
    interval: Duration = Duration.ZERO,
    shouldLimit: (HttpUrl) -> Boolean = { true },
): OkHttpClient.Builder {
    val interceptor = interceptors().firstInstanceOrNull<RateLimitInterceptor>()
        ?: RateLimitInterceptor().also(::addInterceptor)

    interceptor.addRateLimit(permits, period, interval, shouldLimit)

    return this
}

private class RateLimitInterceptor : Interceptor {
    private class RateLimit(
        val permits: Int,
        val period: Duration,
        val interval: Duration,
        val shouldLimit: (HttpUrl) -> Boolean,
    ) {
        val queue = ArrayDeque<TimeStamp>(permits)
        val lock = ReentrantLock(true)
        val retryCondition: Condition = lock.newCondition()
        var lastDispatchTime: Duration? = null
        var sequence = 0L
    }

    private class TimeStamp(
        val id: Long,
        val timestamp: Duration,
    )

    private val rateLimits = mutableListOf<RateLimit>()

    fun addRateLimit(
        permits: Int,
        period: Duration,
        interval: Duration,
        shouldLimit: (HttpUrl) -> Boolean,
    ) {
        rateLimits.add(RateLimit(permits, period, interval, shouldLimit))
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val call = chain.call()
        val request = chain.request()

        if (call.isCanceled()) throw IOException("Canceled")

        val rateLimit = rateLimits.firstOrNull { it.shouldLimit(request.url) }
            ?: return chain.proceed(request)

        val id = rateLimit.acquireSlot(call)
        try {
            val response = chain.proceed(request)
            if (response.networkResponse == null) rateLimit.releaseSlot(id)
            return response
        } catch (e: Exception) {
            rateLimit.releaseSlot(id)
            throw e
        }
    }

    private fun RateLimit.acquireSlot(call: Call): Long = lock.withLock {
        while (true) {
            if (call.isCanceled()) throw IOException("Canceled")

            val now = SystemClock.elapsedRealtime().milliseconds
            while (queue.isNotEmpty() && now - queue.first().timestamp > period) {
                queue.removeFirst()
            }

            val windowWait = if (queue.size < permits) {
                Duration.ZERO
            } else {
                period - (now - queue.first().timestamp)
            }
            val intervalWait = if (lastDispatchTime == null) {
                Duration.ZERO
            } else {
                interval - (now - lastDispatchTime!!)
            }
            val waitTime = maxOf(windowWait, intervalWait, Duration.ZERO)

            if (waitTime == Duration.ZERO) break

            retryCondition.awaitNanos(waitTime.inWholeNanoseconds)
        }

        val now = SystemClock.elapsedRealtime().milliseconds
        val id = sequence++
        queue.addLast(TimeStamp(id, now))
        lastDispatchTime = now
        id
    }

    private fun RateLimit.releaseSlot(id: Long): Unit = lock.withLock {
        val removed = queue.removeAll { it.id == id }
        if (removed) {
            retryCondition.signal()
        }
    }
}
