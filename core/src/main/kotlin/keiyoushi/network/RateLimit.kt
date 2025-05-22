/**
 * Based on the following files and commits and its preceding works:
 * https://github.com/mihonapp/extensions-lib/blob/d96d81e/library/src/main/java/mihonx/network/rateLimit.kt
 * https://github.com/AntsyLich/mihon/commit/f5d8cbe
 */
@file:Suppress("NOTHING_TO_INLINE")

package keiyoushi.network

import android.os.SystemClock
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.util.ArrayDeque
import java.util.concurrent.Semaphore
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * An OkHttp interceptor that enforces rate limiting across all requests.
 *
 * Examples:
 * - `permits = 5`, `period = 1.seconds` =>  5 requests per second
 * - `permits = 10`, `period = 2.minutes` =>  10 requests per 2 minutes
 *
 * @param permits [Int]     Number of requests allowed within a period of units.
 * @param period [Duration] The limiting duration. Defaults to 1.seconds.
 */
inline fun OkHttpClient.Builder.rateLimit(
    permits: Int,
    period: Duration = 1.seconds
): OkHttpClient.Builder = rateLimit(permits, period) { true }

/**
 * An OkHttp interceptor that handles given url host's rate limiting.
 *
 * Examples:
 * - `url = "https://api.manga.example"`, `permits = 5`, `period = 1.seconds` =>  5 requests per second to any url with host "api.manga.example"
 * - `url = "https://cdn.manga.example/image"`, `permits = 10`, `period = 2.minutes`  =>  10 requests per 2 minutes to any url with host "cdn.manga.example"
 *
 * @param url [String]      The url host that this interceptor should handle. Will get url's host by using HttpUrl.host()
 * @param permits [Int]     Number of requests allowed within a period of units.
 * @param period [Duration] The limiting duration. Defaults to 1.seconds.
 */
inline fun OkHttpClient.Builder.rateLimit(
    url: String,
    permits: Int,
    period: Duration = 1.seconds,
): OkHttpClient.Builder = rateLimit(url.toHttpUrl(), permits, period)

/**
 * An OkHttp interceptor that handles given url host's rate limiting.
 *
 * Examples:
 * - `httpUrl = "https://api.manga.example".toHttpUrlOrNull()`, `permits = 5`, `period = 1.seconds` =>  5 requests per second to any url with host "api.manga.example"
 * - `httpUrl = "https://cdn.manga.example/image".toHttpUrlOrNull()`, `permits = 10`, `period = 2.minutes` =>  10 requests per 2 minutes to any url with host "cdn.manga.example
 *
 * @param httpUrl [HttpUrl] The url host that this interceptor should handle. Will get url's host by using HttpUrl.host()
 * @param permits [Int]     Number of requests allowed within a period of units.
 * @param period [Duration] The limiting duration. Defaults to 1.seconds.
 */
inline fun OkHttpClient.Builder.rateLimit(
    httpUrl: HttpUrl,
    permits: Int,
    period: Duration = 1.seconds,
): OkHttpClient.Builder = rateLimit(permits, period) { it.host == httpUrl.host }

/**
 * An OkHttp interceptor that enforces conditional rate limiting based on a given condition.
 *
 * Examples:
 * - `permits = 5`, `period = 1.seconds`, `shouldLimit = { it.host == "api.manga.example" }` => 5 requests per second to any url with host "api.manga.example".
 * - `permits = 10`, `period = 2.minutes`, `shouldLimit = { it.encodedPath.startsWith("/images/") }` => 10 requests per 2 minutes to paths starting with "/images/".
 *
 * @param permits [Int]     Number of requests allowed within a period of units.
 * @param period [Duration] The limiting duration. Defaults to 1.seconds.
 * @param shouldLimit       A predicate to determine whether the rate limit should apply to a given request.
 */
@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
inline fun OkHttpClient.Builder.rateLimit(
    permits: Int,
    period: Duration = 1.seconds,
    crossinline shouldLimit: (HttpUrl) -> Boolean,
): OkHttpClient.Builder = addInterceptor(object : Interceptor {
    private val requestQueue = ArrayDeque<Long>(permits)
    private val rateLimitMillis = period.inWholeMilliseconds
    private val fairLock = Semaphore(1, true)

    override fun intercept(chain: Interceptor.Chain): Response {
        val call = chain.call()
        if (call.isCanceled()) throw IOException("Canceled")

        val request = chain.request()
        if (!shouldLimit(request.url)) return chain.proceed(request)

        try {
            fairLock.acquire()
        } catch (e: InterruptedException) {
            throw IOException(e)
        }

        val requestQueue = this.requestQueue
        val timestamp: Long

        try {
            synchronized(requestQueue) {
                while (requestQueue.size >= permits) { // queue is full, remove expired entries
                    val periodStart = SystemClock.elapsedRealtime() - rateLimitMillis
                    var hasRemovedExpired = false
                    while (!requestQueue.isEmpty() && requestQueue.first <= periodStart) {
                        requestQueue.removeFirst()
                        hasRemovedExpired = true
                    }
                    if (call.isCanceled()) {
                        throw IOException("Canceled")
                    } else if (hasRemovedExpired) {
                        break
                    } else {
                        try { // wait for the first entry to expire, or notified by cached response
                            (requestQueue as Object).wait(requestQueue.first - periodStart)
                        } catch (_: InterruptedException) {
                            continue
                        }
                    }
                }

                // add request to queue
                timestamp = SystemClock.elapsedRealtime()
                requestQueue.addLast(timestamp)
            }
        } finally {
            fairLock.release()
        }

        val response = chain.proceed(request)
        if (response.networkResponse == null) { // response is cached, remove it from queue
            synchronized(requestQueue) {
                if (requestQueue.isEmpty() || timestamp < requestQueue.first) return@synchronized
                requestQueue.removeFirstOccurrence(timestamp)
                (requestQueue as Object).notifyAll()
            }
        }

        return response
    }
})
