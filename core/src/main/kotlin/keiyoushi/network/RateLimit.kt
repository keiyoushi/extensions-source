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
 * Adds a rate limit rule to the builder's [RateLimitInterceptor], creating it if this is the
 * first [rateLimit] call. Chain further calls for additional rules — the first rule whose
 * [shouldLimit] matches the request URL is applied, remaining rules are skipped, so define more
 * specific rules before broader ones:
 * ```
 * OkHttpClient.Builder()
 *     .rateLimit(5)  { it.host == "api.manga.example" }
 *     .rateLimit(20) { it.host == "img.manga.example" }
 *     .build()
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
): OkHttpClient.Builder = apply {
    val rule = RateLimitRule(permits, period, interval, shouldLimit)

    val existing = networkInterceptors().firstInstanceOrNull<RateLimitInterceptor>()
    if (existing != null) {
        existing.addRule(rule)
        return@apply
    }

    if (RateLimitInterceptor.TaggingInterceptor !in interceptors()) {
        addInterceptor(RateLimitInterceptor.TaggingInterceptor)
    }
    addNetworkInterceptor(RateLimitInterceptor(rule))
}

internal class RateLimitRule(
    val permits: Int,
    val period: Duration,
    val interval: Duration,
    val shouldLimit: (HttpUrl) -> Boolean,
)

internal class RateLimitInterceptor(
    rule: RateLimitRule,
) : Interceptor {

    private class RateLimitTag(
        var rateLimitApplied: Boolean = false,
    )

    object TaggingInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request().newBuilder()
                .tag(RateLimitTag::class.java, RateLimitTag())
                .build()

            return chain.proceed(request)
        }
    }

    private class RateLimit(
        val permits: Int,
        val period: Duration,
        val interval: Duration,
        val shouldLimit: (HttpUrl) -> Boolean,
    ) {
        val queue = ArrayDeque<Duration>(permits)
        val lock = ReentrantLock(true)
        val retryCondition: Condition = lock.newCondition()
        var lastDispatchTime: Duration? = null
    }

    private val rateLimits = mutableListOf(RateLimit(rule.permits, rule.period, rule.interval, rule.shouldLimit))

    fun addRule(rule: RateLimitRule) {
        rateLimits += RateLimit(rule.permits, rule.period, rule.interval, rule.shouldLimit)
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val call = chain.call()
        val request = chain.request()

        if (call.isCanceled()) throw IOException("Canceled")

        val rateLimit = rateLimits.firstOrNull { it.shouldLimit(request.url) }
            ?: return chain.proceed(request)

        val state = request.tag(RateLimitTag::class.java)

        if (state?.rateLimitApplied == true) {
            return chain.proceed(request)
        }

        rateLimit.acquireSlot(call)
        state?.rateLimitApplied = true

        return chain.proceed(request)
    }

    private fun RateLimit.acquireSlot(call: Call) = lock.withLock {
        while (true) {
            if (call.isCanceled()) throw IOException("Canceled")
            val now = SystemClock.elapsedRealtime().milliseconds
            while (queue.isNotEmpty() && now - queue.first() >= period) {
                queue.removeFirst()
            }

            val windowWait = if (queue.size < permits) {
                Duration.ZERO
            } else {
                period - (now - queue.first())
            }
            val intervalWait = if (lastDispatchTime == null) {
                Duration.ZERO
            } else {
                interval - (now - lastDispatchTime!!)
            }
            val waitTime = maxOf(windowWait, intervalWait, Duration.ZERO)

            if (waitTime == Duration.ZERO) {
                val ts = SystemClock.elapsedRealtime().milliseconds
                queue.addLast(ts)
                lastDispatchTime = ts
                break
            }
            retryCondition.awaitNanos(waitTime.inWholeNanoseconds)
        }
    }
}
