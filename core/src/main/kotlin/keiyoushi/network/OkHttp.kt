package keiyoushi.network

import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import kotlin.time.Duration.Companion.minutes

/**
 * Default cache control.
 */
private val DEFAULT_CACHE_CONTROL = CacheControl.Builder().maxAge(10.minutes).build()

/**
 * Executes a GET request asynchronously and returns the response.
 *
 * @param url The [HttpUrl] to request.
 * @param headers The headers to include in the request.
 * @param cacheControl The cache control settings for the request.
 * @param ensureSuccess If true, throws an exception if the response code is not 2xx.
 * @return The HTTP [Response].
 */
suspend fun OkHttpClient.get(
    url: HttpUrl,
    headers: Headers,
    cacheControl: CacheControl = DEFAULT_CACHE_CONTROL,
    ensureSuccess: Boolean = true,
): Response {
    val request = Request.Builder()
        .url(url)
        .headers(headers)
        .cacheControl(cacheControl)
        .build()
    val call = newCall(request)

    return if (ensureSuccess) {
        call.awaitSuccess()
    } else {
        call.await()
    }
}

/**
 * Executes a GET request asynchronously using a URL string and returns the response.
 *
 * @param url The URL string to request.
 * @param headers The headers to include in the request.
 * @param cacheControl The cache control settings for the request.
 * @param ensureSuccess If true, throws an exception if the response code is not 2xx.
 * @return The HTTP [Response].
 */
suspend fun OkHttpClient.get(
    url: String,
    headers: Headers,
    cacheControl: CacheControl = DEFAULT_CACHE_CONTROL,
    ensureSuccess: Boolean = true,
): Response = get(url.toHttpUrl(), headers, cacheControl, ensureSuccess)

/**
 * Executes a GET request asynchronously, automatically retrieving the headers from the
 * current [HttpSource] context receiver.
 *
 * @param url The [HttpUrl] to request.
 * @param cacheControl The cache control settings for the request.
 * @param ensureSuccess If true, throws an exception if the response code is not 2xx.
 * @return The HTTP [Response].
 */
context(source: HttpSource)
suspend fun OkHttpClient.get(
    url: HttpUrl,
    cacheControl: CacheControl = DEFAULT_CACHE_CONTROL,
    ensureSuccess: Boolean = true,
): Response = get(url, source.headers, cacheControl, ensureSuccess)

/**
 * Executes a GET request asynchronously using a URL string, automatically retrieving the
 * headers from the current [HttpSource] context receiver.
 *
 * @param url The URL string to request.
 * @param cacheControl The cache control settings for the request.
 * @param ensureSuccess If true, throws an exception if the response code is not 2xx.
 * @return The HTTP [Response].
 */
context(source: HttpSource)
suspend fun OkHttpClient.get(
    url: String,
    cacheControl: CacheControl = DEFAULT_CACHE_CONTROL,
    ensureSuccess: Boolean = true,
): Response = get(url, source.headers, cacheControl, ensureSuccess)

/**
 * Executes a POST request asynchronously and returns the response.
 *
 * @param url The [HttpUrl] to request.
 * @param headers The headers to include in the request.
 * @param body The request body payload.
 * @param ensureSuccess If true, throws an exception if the response code is not 2xx.
 * @return The HTTP [Response].
 */
suspend fun OkHttpClient.post(
    url: HttpUrl,
    headers: Headers,
    body: RequestBody,
    ensureSuccess: Boolean = true,
): Response {
    val request = Request.Builder()
        .url(url)
        .headers(headers)
        .post(body)
        .build()
    val call = newCall(request)

    return if (ensureSuccess) {
        call.awaitSuccess()
    } else {
        call.await()
    }
}

/**
 * Executes a POST request asynchronously using a URL string and returns the response.
 *
 * @param url The URL string to request.
 * @param headers The headers to include in the request.
 * @param body The request body payload.
 * @param ensureSuccess If true, throws an exception if the response code is not 2xx.
 * @return The HTTP [Response].
 */
suspend fun OkHttpClient.post(
    url: String,
    headers: Headers,
    body: RequestBody,
    ensureSuccess: Boolean = true,
): Response = post(url.toHttpUrl(), headers, body, ensureSuccess)

/**
 * Executes a POST request asynchronously, automatically retrieving the
 * headers from the current [HttpSource] context receiver.
 *
 * @param url The [HttpUrl] to request.
 * @param body The request body payload.
 * @param ensureSuccess If true, throws an exception if the response code is not 2xx.
 * @return The HTTP [Response].
 */
context(source: HttpSource)
suspend fun OkHttpClient.post(
    url: HttpUrl,
    body: RequestBody,
    ensureSuccess: Boolean = true,
): Response = post(url, source.headers, body, ensureSuccess)

/**
 * Executes a POST request asynchronously using a URL string, automatically retrieving the
 * headers from the current [HttpSource] context receiver.
 *
 * @param url The URL string to request.
 * @param body The request body payload.
 * @param ensureSuccess If true, throws an exception if the response code is not 2xx.
 * @return The HTTP [Response].
 */
context(source: HttpSource)
suspend fun OkHttpClient.post(
    url: String,
    body: RequestBody,
    ensureSuccess: Boolean = true,
): Response = post(url, source.headers, body, ensureSuccess)

/**
 * Executes a PUT request asynchronously and returns the response.
 *
 * @param url The [HttpUrl] to request.
 * @param headers The headers to include in the request.
 * @param body The request body payload.
 * @param ensureSuccess If true, throws an exception if the response code is not 2xx.
 * @return The HTTP [Response].
 */
suspend fun OkHttpClient.put(
    url: HttpUrl,
    headers: Headers,
    body: RequestBody,
    ensureSuccess: Boolean = true,
): Response {
    val request = Request.Builder()
        .url(url)
        .headers(headers)
        .put(body)
        .build()
    val call = newCall(request)

    return if (ensureSuccess) {
        call.awaitSuccess()
    } else {
        call.await()
    }
}

/**
 * Executes a PUT request asynchronously using a URL string and returns the response.
 *
 * @param url The URL string to request.
 * @param headers The headers to include in the request.
 * @param body The request body payload.
 * @param ensureSuccess If true, throws an exception if the response code is not 2xx.
 * @return The HTTP [Response].
 */
suspend fun OkHttpClient.put(
    url: String,
    headers: Headers,
    body: RequestBody,
    ensureSuccess: Boolean = true,
): Response = put(url.toHttpUrl(), headers, body, ensureSuccess)

/**
 * Executes a PUT request asynchronously using a URL string, automatically retrieving the
 * headers from the current [HttpSource] context receiver.
 *
 * @param url The URL string to request.
 * @param body The request body payload.
 * @param ensureSuccess If true, throws an exception if the response code is not 2xx.
 * @return The HTTP [Response].
 */
context(source: HttpSource)
suspend fun OkHttpClient.put(
    url: String,
    body: RequestBody,
    ensureSuccess: Boolean = true,
): Response = put(url, source.headers, body, ensureSuccess)

/**
 * Executes a PUT request asynchronously, automatically retrieving the
 * headers from the current [HttpSource] context receiver.
 *
 * @param url The [HttpUrl] to request.
 * @param body The request body payload.
 * @param ensureSuccess If true, throws an exception if the response code is not 2xx.
 * @return The HTTP [Response].
 */
context(source: HttpSource)
suspend fun OkHttpClient.put(
    url: HttpUrl,
    body: RequestBody,
    ensureSuccess: Boolean = true,
): Response = put(url, source.headers, body, ensureSuccess)

/**
 * Executes a HEAD request asynchronously and returns the response.
 *
 * @param url The [HttpUrl] to request.
 * @param headers The headers to include in the request.
 * @param cacheControl The cache control settings for the request.
 * @param ensureSuccess If true, throws an exception if the response code is not 2xx.
 * @return The HTTP [Response].
 */
suspend fun OkHttpClient.head(
    url: HttpUrl,
    headers: Headers,
    cacheControl: CacheControl = DEFAULT_CACHE_CONTROL,
    ensureSuccess: Boolean = true,
): Response {
    val request = Request.Builder()
        .url(url)
        .headers(headers)
        .cacheControl(cacheControl)
        .head()
        .build()
    val call = newCall(request)

    return if (ensureSuccess) {
        call.awaitSuccess()
    } else {
        call.await()
    }
}

/**
 * Executes a HEAD request asynchronously using a URL string and returns the response.
 *
 * @param url The URL string to request.
 * @param headers The headers to include in the request.
 * @param cacheControl The cache control settings for the request.
 * @param ensureSuccess If true, throws an exception if the response code is not 2xx.
 * @return The HTTP [Response].
 */
suspend fun OkHttpClient.head(
    url: String,
    headers: Headers,
    cacheControl: CacheControl = DEFAULT_CACHE_CONTROL,
    ensureSuccess: Boolean = true,
): Response = head(url.toHttpUrl(), headers, cacheControl, ensureSuccess)

/**
 * Executes a HEAD request asynchronously using a URL string, automatically retrieving the
 * headers from the current [HttpSource] context receiver.
 *
 * @param url The URL string to request.
 * @param cacheControl The cache control settings for the request.
 * @param ensureSuccess If true, throws an exception if the response code is not 2xx.
 * @return The HTTP [Response].
 */
context(source: HttpSource)
suspend fun OkHttpClient.head(
    url: String,
    cacheControl: CacheControl = DEFAULT_CACHE_CONTROL,
    ensureSuccess: Boolean = true,
): Response = head(url, source.headers, cacheControl, ensureSuccess)

/**
 * Executes a HEAD request asynchronously, automatically retrieving the
 * headers from the current [HttpSource] context receiver.
 *
 * @param url The [HttpUrl] to request.
 * @param cacheControl The cache control settings for the request.
 * @param ensureSuccess If true, throws an exception if the response code is not 2xx.
 * @return The HTTP [Response].
 */
context(source: HttpSource)
suspend fun OkHttpClient.head(
    url: HttpUrl,
    cacheControl: CacheControl = DEFAULT_CACHE_CONTROL,
    ensureSuccess: Boolean = true,
): Response = head(url, source.headers, cacheControl, ensureSuccess)
