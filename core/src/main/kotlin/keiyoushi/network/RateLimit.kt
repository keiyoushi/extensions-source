package keiyoushi.network

import android.os.SystemClock
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
 * Rate limit rule. Use with [rateLimits] to enforce per-URL request throttling.
 *
 * @param permits     Requests allowed per [period].
 * @param period      Sliding-window duration.
 * @param interval    Minimum gap between consecutive dispatches. Smooths bursts —
 *                    `permits=10, interval=100.ms` allows 10/s but spaced ≥100ms apart.
 * @param shouldLimit Whether this rule applies to a given URL.
 */
data class RateLimit(
    val permits: Int,
    val period: Duration = 1.seconds,
    val interval: Duration = Duration.ZERO,
    val shouldLimit: (HttpUrl) -> Boolean,
)

/**
 * Adds an interceptor enforcing one or more rate limits.
 *
 * The first rule whose [RateLimit.shouldLimit] matches the request URL is applied;
 * remaining rules are skipped. Define more specific rules before broader ones:
 * ```
 * rateLimits(
 *     RateLimit(5)  { it.host == "api.manga.example" },
 *     RateLimit(20) { it.host == "img.manga.example" },
 * )
 * ```
 */
fun OkHttpClient.Builder.rateLimits(
    vararg limits: RateLimit,
): OkHttpClient.Builder = addInterceptor(RateLimitInterceptor(limits.toList()))

/**
 * Adds an interceptor enforcing a single rate limit.
 *
 * For multiple rules, use [rateLimits].
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
): OkHttpClient.Builder = rateLimits(RateLimit(permits, period, interval, shouldLimit))

private class RateLimitInterceptor(limits: List<RateLimit>) : Interceptor {

    private class Limiter(val rule: RateLimit) {
        val periodMillis = rule.period.inWholeMilliseconds
        val intervalMillis = rule.interval.inWholeMilliseconds
        val queue = ArrayDeque<Long>(rule.permits)
        val lock = ReentrantLock(true)
        val retryCondition: Condition = lock.newCondition()
        var lastDispatchTime = 0L
    }

    private val limiters = limits.map(::Limiter)

    override fun intercept(chain: Interceptor.Chain): Response {
        val call = chain.call()
        val request = chain.request()

        if (call.isCanceled()) throw IOException("Canceled")

        val matched = limiters.firstOrNull { it.rule.shouldLimit(request.url) }
            ?: return chain.proceed(request)

        val timestamp = matched.acquireSlot(call)
        val response = chain.proceed(request)

        if (response.networkResponse == null) matched.releaseSlot(timestamp)

        return response
    }

    private fun Limiter.acquireSlot(call: Call): Long = lock.withLock {
        while (true) {
            if (call.isCanceled()) throw IOException("Canceled")

            val now = SystemClock.elapsedRealtime()
            while (queue.isNotEmpty() && queue.first() <= now - periodMillis) queue.removeFirst()

            val windowWait = if (queue.size < rule.permits) 0L else queue.first() - (now - periodMillis)
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

    private fun Limiter.releaseSlot(timestamp: Long): Unit = lock.withLock {
        if (queue.isEmpty() || timestamp < queue.first()) return
        queue.removeFirstOccurrence(timestamp)
        retryCondition.signalAll()
    }
}
