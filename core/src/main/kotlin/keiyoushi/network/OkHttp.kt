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
val DEFAULT_CACHE_CONTROL: CacheControl = CacheControl.Builder().maxAge(10.minutes).build()

/**
 * Executes a GET request asynchronously the way a browser would make it, and returns the response.
 *
 * @param url The [HttpUrl] to request.
 * @param headers The headers to include in the request.
 * @param fetch How a browser would make the request, see [Fetch].
 * @param cacheControl The cache control settings for the request.
 * @param ensureSuccess If true, throws an exception if the response code is not 2xx.
 * @return The HTTP [Response].
 */
suspend fun OkHttpClient.get(
    url: HttpUrl,
    headers: Headers,
    fetch: Fetch,
    cacheControl: CacheControl = DEFAULT_CACHE_CONTROL,
    ensureSuccess: Boolean = true,
): Response = call(
    Request.Builder()
        .url(url)
        .headers(headers)
        .cacheControl(cacheControl)
        .fetch(fetch)
        .build(),
    ensureSuccess,
)

/**
 * Executes a GET request asynchronously using a URL string the way a browser would make it, and returns the response.
 *
 * @param url The URL string to request.
 * @param headers The headers to include in the request.
 * @param fetch How a browser would make the request, see [Fetch].
 * @param cacheControl The cache control settings for the request.
 * @param ensureSuccess If true, throws an exception if the response code is not 2xx.
 * @return The HTTP [Response].
 */
suspend fun OkHttpClient.get(
    url: String,
    headers: Headers,
    fetch: Fetch,
    cacheControl: CacheControl = DEFAULT_CACHE_CONTROL,
    ensureSuccess: Boolean = true,
): Response = get(url.toHttpUrl(), headers, fetch, cacheControl, ensureSuccess)

/**
 * Executes a GET request asynchronously the way a browser would make it, automatically retrieving the headers from
 * the current [HttpSource] context receiver.
 *
 * @param url The [HttpUrl] to request.
 * @param fetch How a browser would make the request, see [Fetch].
 * @param cacheControl The cache control settings for the request.
 * @param ensureSuccess If true, throws an exception if the response code is not 2xx.
 * @return The HTTP [Response].
 */
context(source: HttpSource)
suspend fun OkHttpClient.get(
    url: HttpUrl,
    fetch: Fetch,
    cacheControl: CacheControl = DEFAULT_CACHE_CONTROL,
    ensureSuccess: Boolean = true,
): Response = get(url, source.headers, fetch, cacheControl, ensureSuccess)

/**
 * Executes a GET request asynchronously using a URL string the way a browser would make it, automatically retrieving
 * the headers from the current [HttpSource] context receiver.
 *
 * @param url The URL string to request.
 * @param fetch How a browser would make the request, see [Fetch].
 * @param cacheControl The cache control settings for the request.
 * @param ensureSuccess If true, throws an exception if the response code is not 2xx.
 * @return The HTTP [Response].
 */
context(source: HttpSource)
suspend fun OkHttpClient.get(
    url: String,
    fetch: Fetch,
    cacheControl: CacheControl = DEFAULT_CACHE_CONTROL,
    ensureSuccess: Boolean = true,
): Response = get(url.toHttpUrl(), source.headers, fetch, cacheControl, ensureSuccess)

/**
 * Executes a GET request asynchronously and returns the response. The request goes out as built, see [Fetch.NONE].
 *
 * @param url The [HttpUrl] to request.
 * @param headers The headers to include in the request.
 * @param cacheControl The cache control settings for the request.
 * @param ensureSuccess If true, throws an exception if the response code is not 2xx.
 * @return The HTTP [Response].
 */
@Deprecated("Pass a Fetch: DOCUMENT for pages, XHR for API and ajax calls, IMAGE for images, SCRIPT for scripts. NONE only if the request has to go out as built.")
suspend fun OkHttpClient.get(
    url: HttpUrl,
    headers: Headers,
    cacheControl: CacheControl = DEFAULT_CACHE_CONTROL,
    ensureSuccess: Boolean = true,
): Response = get(url, headers, Fetch.NONE, cacheControl, ensureSuccess)

/**
 * Executes a GET request asynchronously using a URL string and returns the response. The request goes out as built,
 * see [Fetch.NONE].
 *
 * @param url The URL string to request.
 * @param headers The headers to include in the request.
 * @param cacheControl The cache control settings for the request.
 * @param ensureSuccess If true, throws an exception if the response code is not 2xx.
 * @return The HTTP [Response].
 */
@Deprecated("Pass a Fetch: DOCUMENT for pages, XHR for API and ajax calls, IMAGE for images, SCRIPT for scripts. NONE only if the request has to go out as built.")
suspend fun OkHttpClient.get(
    url: String,
    headers: Headers,
    cacheControl: CacheControl = DEFAULT_CACHE_CONTROL,
    ensureSuccess: Boolean = true,
): Response = get(url.toHttpUrl(), headers, Fetch.NONE, cacheControl, ensureSuccess)

/**
 * Executes a GET request asynchronously, automatically retrieving the headers from the
 * current [HttpSource] context receiver. The request goes out as built, see [Fetch.NONE].
 *
 * @param url The [HttpUrl] to request.
 * @param cacheControl The cache control settings for the request.
 * @param ensureSuccess If true, throws an exception if the response code is not 2xx.
 * @return The HTTP [Response].
 */
@Deprecated("Pass a Fetch: DOCUMENT for pages, XHR for API and ajax calls, IMAGE for images, SCRIPT for scripts. NONE only if the request has to go out as built.")
context(source: HttpSource)
suspend fun OkHttpClient.get(
    url: HttpUrl,
    cacheControl: CacheControl = DEFAULT_CACHE_CONTROL,
    ensureSuccess: Boolean = true,
): Response = get(url, source.headers, Fetch.NONE, cacheControl, ensureSuccess)

/**
 * Executes a GET request asynchronously using a URL string, automatically retrieving the
 * headers from the current [HttpSource] context receiver. The request goes out as built, see [Fetch.NONE].
 *
 * @param url The URL string to request.
 * @param cacheControl The cache control settings for the request.
 * @param ensureSuccess If true, throws an exception if the response code is not 2xx.
 * @return The HTTP [Response].
 */
@Deprecated("Pass a Fetch: DOCUMENT for pages, XHR for API and ajax calls, IMAGE for images, SCRIPT for scripts. NONE only if the request has to go out as built.")
context(source: HttpSource)
suspend fun OkHttpClient.get(
    url: String,
    cacheControl: CacheControl = DEFAULT_CACHE_CONTROL,
    ensureSuccess: Boolean = true,
): Response = get(url.toHttpUrl(), source.headers, Fetch.NONE, cacheControl, ensureSuccess)

/**
 * Executes a POST request asynchronously the way a browser would make it, and returns the response.
 *
 * @param url The [HttpUrl] to request.
 * @param headers The headers to include in the request.
 * @param fetch How a browser would make the request, see [Fetch].
 * @param body The request body payload.
 * @param ensureSuccess If true, throws an exception if the response code is not 2xx.
 * @return The HTTP [Response].
 */
suspend fun OkHttpClient.post(
    url: HttpUrl,
    headers: Headers,
    fetch: Fetch,
    body: RequestBody,
    ensureSuccess: Boolean = true,
): Response = call(
    Request.Builder()
        .url(url)
        .headers(headers)
        .post(body)
        .fetch(fetch)
        .build(),
    ensureSuccess,
)

/**
 * Executes a POST request asynchronously using a URL string the way a browser would make it, and returns the
 * response.
 *
 * @param url The URL string to request.
 * @param headers The headers to include in the request.
 * @param fetch How a browser would make the request, see [Fetch].
 * @param body The request body payload.
 * @param ensureSuccess If true, throws an exception if the response code is not 2xx.
 * @return The HTTP [Response].
 */
suspend fun OkHttpClient.post(
    url: String,
    headers: Headers,
    fetch: Fetch,
    body: RequestBody,
    ensureSuccess: Boolean = true,
): Response = post(url.toHttpUrl(), headers, fetch, body, ensureSuccess)

/**
 * Executes a POST request asynchronously the way a browser would make it, automatically retrieving the headers from
 * the current [HttpSource] context receiver.
 *
 * @param url The [HttpUrl] to request.
 * @param fetch How a browser would make the request, see [Fetch].
 * @param body The request body payload.
 * @param ensureSuccess If true, throws an exception if the response code is not 2xx.
 * @return The HTTP [Response].
 */
context(source: HttpSource)
suspend fun OkHttpClient.post(
    url: HttpUrl,
    fetch: Fetch,
    body: RequestBody,
    ensureSuccess: Boolean = true,
): Response = post(url, source.headers, fetch, body, ensureSuccess)

/**
 * Executes a POST request asynchronously using a URL string the way a browser would make it, automatically
 * retrieving the headers from the current [HttpSource] context receiver.
 *
 * @param url The URL string to request.
 * @param fetch How a browser would make the request, see [Fetch].
 * @param body The request body payload.
 * @param ensureSuccess If true, throws an exception if the response code is not 2xx.
 * @return The HTTP [Response].
 */
context(source: HttpSource)
suspend fun OkHttpClient.post(
    url: String,
    fetch: Fetch,
    body: RequestBody,
    ensureSuccess: Boolean = true,
): Response = post(url.toHttpUrl(), source.headers, fetch, body, ensureSuccess)

/**
 * Executes a POST request asynchronously and returns the response. The request goes out as built, see [Fetch.NONE].
 *
 * @param url The [HttpUrl] to request.
 * @param headers The headers to include in the request.
 * @param body The request body payload.
 * @param ensureSuccess If true, throws an exception if the response code is not 2xx.
 * @return The HTTP [Response].
 */
@Deprecated("Pass a Fetch: DOCUMENT for pages, XHR for API and ajax calls, IMAGE for images, SCRIPT for scripts. NONE only if the request has to go out as built.")
suspend fun OkHttpClient.post(
    url: HttpUrl,
    headers: Headers,
    body: RequestBody,
    ensureSuccess: Boolean = true,
): Response = post(url, headers, Fetch.NONE, body, ensureSuccess)

/**
 * Executes a POST request asynchronously using a URL string and returns the response. The request goes out as
 * built, see [Fetch.NONE].
 *
 * @param url The URL string to request.
 * @param headers The headers to include in the request.
 * @param body The request body payload.
 * @param ensureSuccess If true, throws an exception if the response code is not 2xx.
 * @return The HTTP [Response].
 */
@Deprecated("Pass a Fetch: DOCUMENT for pages, XHR for API and ajax calls, IMAGE for images, SCRIPT for scripts. NONE only if the request has to go out as built.")
suspend fun OkHttpClient.post(
    url: String,
    headers: Headers,
    body: RequestBody,
    ensureSuccess: Boolean = true,
): Response = post(url.toHttpUrl(), headers, Fetch.NONE, body, ensureSuccess)

/**
 * Executes a POST request asynchronously, automatically retrieving the
 * headers from the current [HttpSource] context receiver. The request goes out as built, see [Fetch.NONE].
 *
 * @param url The [HttpUrl] to request.
 * @param body The request body payload.
 * @param ensureSuccess If true, throws an exception if the response code is not 2xx.
 * @return The HTTP [Response].
 */
@Deprecated("Pass a Fetch: DOCUMENT for pages, XHR for API and ajax calls, IMAGE for images, SCRIPT for scripts. NONE only if the request has to go out as built.")
context(source: HttpSource)
suspend fun OkHttpClient.post(
    url: HttpUrl,
    body: RequestBody,
    ensureSuccess: Boolean = true,
): Response = post(url, source.headers, Fetch.NONE, body, ensureSuccess)

/**
 * Executes a POST request asynchronously using a URL string, automatically retrieving the
 * headers from the current [HttpSource] context receiver. The request goes out as built, see [Fetch.NONE].
 *
 * @param url The URL string to request.
 * @param body The request body payload.
 * @param ensureSuccess If true, throws an exception if the response code is not 2xx.
 * @return The HTTP [Response].
 */
@Deprecated("Pass a Fetch: DOCUMENT for pages, XHR for API and ajax calls, IMAGE for images, SCRIPT for scripts. NONE only if the request has to go out as built.")
context(source: HttpSource)
suspend fun OkHttpClient.post(
    url: String,
    body: RequestBody,
    ensureSuccess: Boolean = true,
): Response = post(url.toHttpUrl(), source.headers, Fetch.NONE, body, ensureSuccess)

/**
 * Executes a [Request] built by hand asynchronously and returns the response, for what the helpers above don't
 * cover: other methods, or a request built from another one. It goes out as built unless it has a [Fetch] set with
 * `Request.Builder.fetch`.
 *
 * @param request The request to send.
 * @param ensureSuccess If true, throws an exception if the response code is not 2xx.
 * @return The HTTP [Response].
 */
suspend fun OkHttpClient.call(
    request: Request,
    ensureSuccess: Boolean = true,
): Response {
    val call = newCall(request)
    return if (ensureSuccess) call.awaitSuccess() else call.await()
}

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
): Response = call(
    Request.Builder()
        .url(url)
        .headers(headers)
        .put(body)
        .build(),
    ensureSuccess,
)

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
): Response = call(
    Request.Builder()
        .url(url)
        .headers(headers)
        .cacheControl(cacheControl)
        .head()
        .build(),
    ensureSuccess,
)

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
