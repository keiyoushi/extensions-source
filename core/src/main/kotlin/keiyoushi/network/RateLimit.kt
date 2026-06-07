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
        val periodMillis = period.inWholeMilliseconds
        val intervalMillis = interval.inWholeMilliseconds
        val queue = ArrayDeque<Long>(permits)
        val lock = ReentrantLock(true)
        val retryCondition: Condition = lock.newCondition()
        var lastDispatchTime = 0L
    }

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

        val timestamp = rateLimit.acquireSlot(call)

        try {
            val response = chain.proceed(request)
            if (response.networkResponse == null) rateLimit.releaseSlot(timestamp)
            return response
        } catch (e: Exception) {
            rateLimit.releaseSlot(timestamp)
            throw e
        }
    }

    private fun RateLimit.acquireSlot(call: Call): Long = lock.withLock {
        while (true) {
            if (call.isCanceled()) throw IOException("Canceled")

            val now = SystemClock.elapsedRealtime()
            while (queue.isNotEmpty() && queue.first() <= now - periodMillis) queue.removeFirst()

            val windowWait = if (queue.size < permits) 0L else queue.first() - (now - periodMillis)
            val intervalWait = lastDispatchTime + intervalMillis - now
            val waitTime = maxOf(windowWait, intervalWait, 0L)

            if (waitTime == 0L) break
            retryCondition.awaitNanos(waitTime * 1_000_000L)
        }

        val ts = SystemClock.elapsedRealtime()
        queue.addLast(ts)
        lastDispatchTime = ts
        ts
    }

    private fun RateLimit.releaseSlot(timestamp: Long): Unit = lock.withLock {
        if (queue.isEmpty() || timestamp < queue.first()) return
        queue.removeFirstOccurrence(timestamp)
        retryCondition.signal()
    }
}
