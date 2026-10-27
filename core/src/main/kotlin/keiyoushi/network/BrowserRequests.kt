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

/**
 * The kind of request a browser makes, named after its DevTools network filters: a page load (Doc), the
 * page's own `fetch()`/XHR calls (Fetch/XHR) or an image (Img).
 */
internal enum class RequestKind { DOCUMENT, XHR, IMAGE }

/** Marks a request as a page load, with the headers a browser sends when opening a page. */
fun Request.Builder.forDocument(): Request.Builder = tag(RequestKind::class.java, RequestKind.DOCUMENT)

/** Marks a request as the page's own `fetch()`/XHR call, e.g. a JSON API or an ajax endpoint. */
fun Request.Builder.forXhr(): Request.Builder = tag(RequestKind::class.java, RequestKind.XHR)

/** Marks a request as an image load. */
fun Request.Builder.forImage(): Request.Builder = tag(RequestKind::class.java, RequestKind.IMAGE)

internal val Request.kind: RequestKind? get() = tag(RequestKind::class.java)

/** GETs a page the way a browser opens it. */
suspend fun OkHttpClient.getDocument(
    url: HttpUrl,
    headers: Headers,
    cacheControl: CacheControl = DEFAULT_CACHE_CONTROL,
    ensureSuccess: Boolean = true,
): Response = send(Request.Builder().url(url).headers(headers).cacheControl(cacheControl).forDocument(), ensureSuccess)

/** GETs a page the way a browser opens it. */
suspend fun OkHttpClient.getDocument(
    url: String,
    headers: Headers,
    cacheControl: CacheControl = DEFAULT_CACHE_CONTROL,
    ensureSuccess: Boolean = true,
): Response = getDocument(url.toHttpUrl(), headers, cacheControl, ensureSuccess)

/** GETs a page the way a browser opens it, with the source's headers. */
context(source: HttpSource)
suspend fun OkHttpClient.getDocument(
    url: HttpUrl,
    cacheControl: CacheControl = DEFAULT_CACHE_CONTROL,
    ensureSuccess: Boolean = true,
): Response = getDocument(url, source.headers, cacheControl, ensureSuccess)

/** GETs a page the way a browser opens it, with the source's headers. */
context(source: HttpSource)
suspend fun OkHttpClient.getDocument(
    url: String,
    cacheControl: CacheControl = DEFAULT_CACHE_CONTROL,
    ensureSuccess: Boolean = true,
): Response = getDocument(url.toHttpUrl(), source.headers, cacheControl, ensureSuccess)

/** GETs the way the page's own `fetch()`/XHR does. */
suspend fun OkHttpClient.getXhr(
    url: HttpUrl,
    headers: Headers,
    cacheControl: CacheControl = DEFAULT_CACHE_CONTROL,
    ensureSuccess: Boolean = true,
): Response = send(Request.Builder().url(url).headers(headers).cacheControl(cacheControl).forXhr(), ensureSuccess)

/** GETs the way the page's own `fetch()`/XHR does. */
suspend fun OkHttpClient.getXhr(
    url: String,
    headers: Headers,
    cacheControl: CacheControl = DEFAULT_CACHE_CONTROL,
    ensureSuccess: Boolean = true,
): Response = getXhr(url.toHttpUrl(), headers, cacheControl, ensureSuccess)

/** GETs the way the page's own `fetch()`/XHR does, with the source's headers. */
context(source: HttpSource)
suspend fun OkHttpClient.getXhr(
    url: HttpUrl,
    cacheControl: CacheControl = DEFAULT_CACHE_CONTROL,
    ensureSuccess: Boolean = true,
): Response = getXhr(url, source.headers, cacheControl, ensureSuccess)

/** GETs the way the page's own `fetch()`/XHR does, with the source's headers. */
context(source: HttpSource)
suspend fun OkHttpClient.getXhr(
    url: String,
    cacheControl: CacheControl = DEFAULT_CACHE_CONTROL,
    ensureSuccess: Boolean = true,
): Response = getXhr(url.toHttpUrl(), source.headers, cacheControl, ensureSuccess)

/** POSTs the way the page's own `fetch()`/XHR does. */
suspend fun OkHttpClient.postXhr(
    url: HttpUrl,
    headers: Headers,
    body: RequestBody,
    ensureSuccess: Boolean = true,
): Response = send(Request.Builder().url(url).headers(headers).post(body).forXhr(), ensureSuccess)

/** POSTs the way the page's own `fetch()`/XHR does. */
suspend fun OkHttpClient.postXhr(
    url: String,
    headers: Headers,
    body: RequestBody,
    ensureSuccess: Boolean = true,
): Response = postXhr(url.toHttpUrl(), headers, body, ensureSuccess)

/** POSTs the way the page's own `fetch()`/XHR does, with the source's headers. */
context(source: HttpSource)
suspend fun OkHttpClient.postXhr(
    url: HttpUrl,
    body: RequestBody,
    ensureSuccess: Boolean = true,
): Response = postXhr(url, source.headers, body, ensureSuccess)

/** POSTs the way the page's own `fetch()`/XHR does, with the source's headers. */
context(source: HttpSource)
suspend fun OkHttpClient.postXhr(
    url: String,
    body: RequestBody,
    ensureSuccess: Boolean = true,
): Response = postXhr(url.toHttpUrl(), source.headers, body, ensureSuccess)

/** GETs an image the way a browser's `<img>` does. */
suspend fun OkHttpClient.getImage(
    url: HttpUrl,
    headers: Headers,
    cacheControl: CacheControl = DEFAULT_CACHE_CONTROL,
    ensureSuccess: Boolean = true,
): Response = send(Request.Builder().url(url).headers(headers).cacheControl(cacheControl).forImage(), ensureSuccess)

/** GETs an image the way a browser's `<img>` does. */
suspend fun OkHttpClient.getImage(
    url: String,
    headers: Headers,
    cacheControl: CacheControl = DEFAULT_CACHE_CONTROL,
    ensureSuccess: Boolean = true,
): Response = getImage(url.toHttpUrl(), headers, cacheControl, ensureSuccess)

/** GETs an image the way a browser's `<img>` does, with the source's headers. */
context(source: HttpSource)
suspend fun OkHttpClient.getImage(
    url: HttpUrl,
    cacheControl: CacheControl = DEFAULT_CACHE_CONTROL,
    ensureSuccess: Boolean = true,
): Response = getImage(url, source.headers, cacheControl, ensureSuccess)

/** GETs an image the way a browser's `<img>` does, with the source's headers. */
context(source: HttpSource)
suspend fun OkHttpClient.getImage(
    url: String,
    cacheControl: CacheControl = DEFAULT_CACHE_CONTROL,
    ensureSuccess: Boolean = true,
): Response = getImage(url.toHttpUrl(), source.headers, cacheControl, ensureSuccess)

private suspend fun OkHttpClient.send(request: Request.Builder, ensureSuccess: Boolean): Response {
    val call = newCall(request.build())
    return if (ensureSuccess) call.awaitSuccess() else call.await()
}
