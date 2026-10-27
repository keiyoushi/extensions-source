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
 * page's own `fetch()`/XHR calls (Fetch/XHR), an image (Img), a script (JS), a stylesheet (CSS) or audio and
 * video (Media).
 */
internal enum class RequestKind { DOCUMENT, XHR, IMAGE, SCRIPT, STYLE, AUDIO, VIDEO }

/** Marks the request as a page load, with the headers a browser sends when opening a page. Shows under Doc in DevTools' Network tab. */
fun Request.Builder.forDocument(): Request.Builder = tag(RequestKind::class.java, RequestKind.DOCUMENT)

/** Marks the request as the page's own `fetch()`/XHR call, e.g. a JSON API or an ajax endpoint. Shows under Fetch/XHR in DevTools' Network tab. */
fun Request.Builder.forXhr(): Request.Builder = tag(RequestKind::class.java, RequestKind.XHR)

/** Marks the request as an image load. Shows under Img in DevTools' Network tab. */
fun Request.Builder.forImage(): Request.Builder = tag(RequestKind::class.java, RequestKind.IMAGE)

/** Marks the request as a script load, e.g. a JS bundle read for an API key or a route. Shows under JS in DevTools' Network tab. */
fun Request.Builder.forScript(): Request.Builder = tag(RequestKind::class.java, RequestKind.SCRIPT)

/** Marks the request as a stylesheet load. Shows under CSS in DevTools' Network tab. */
fun Request.Builder.forStyle(): Request.Builder = tag(RequestKind::class.java, RequestKind.STYLE)

/** Marks the request as an `<audio>` load. Shows under Media in DevTools' Network tab. */
fun Request.Builder.forAudio(): Request.Builder = tag(RequestKind::class.java, RequestKind.AUDIO)

/** Marks the request as a `<video>` load. Shows under Media in DevTools' Network tab. */
fun Request.Builder.forVideo(): Request.Builder = tag(RequestKind::class.java, RequestKind.VIDEO)

/** A request that goes out as built, see [getRaw]. */
internal object RawRequest

/** Marks the request to go out as built, without the headers a browser adds. See [getRaw]. */
fun Request.Builder.raw(): Request.Builder = tag(RawRequest::class.java, RawRequest)

internal val Request.kind: RequestKind? get() = tag(RequestKind::class.java)

internal val Request.isRaw: Boolean get() = tag(RawRequest::class.java) != null

/** GETs a page the way a browser opens it. Shows under Doc in DevTools' Network tab. */
suspend fun OkHttpClient.getDocument(
    url: HttpUrl,
    headers: Headers,
    cacheControl: CacheControl = DEFAULT_CACHE_CONTROL,
    ensureSuccess: Boolean = true,
): Response = send(Request.Builder().url(url).headers(headers).cacheControl(cacheControl).forDocument(), ensureSuccess)

/** GETs a page the way a browser opens it. Shows under Doc in DevTools' Network tab. */
suspend fun OkHttpClient.getDocument(
    url: String,
    headers: Headers,
    cacheControl: CacheControl = DEFAULT_CACHE_CONTROL,
    ensureSuccess: Boolean = true,
): Response = getDocument(url.toHttpUrl(), headers, cacheControl, ensureSuccess)

/** GETs a page the way a browser opens it. Shows under Doc in DevTools' Network tab. With the source's headers. */
context(source: HttpSource)
suspend fun OkHttpClient.getDocument(
    url: HttpUrl,
    cacheControl: CacheControl = DEFAULT_CACHE_CONTROL,
    ensureSuccess: Boolean = true,
): Response = getDocument(url, source.headers, cacheControl, ensureSuccess)

/** GETs a page the way a browser opens it. Shows under Doc in DevTools' Network tab. With the source's headers. */
context(source: HttpSource)
suspend fun OkHttpClient.getDocument(
    url: String,
    cacheControl: CacheControl = DEFAULT_CACHE_CONTROL,
    ensureSuccess: Boolean = true,
): Response = getDocument(url.toHttpUrl(), source.headers, cacheControl, ensureSuccess)

/** GETs the way the page's own `fetch()`/XHR does: JSON APIs, ajax. Shows under Fetch/XHR in DevTools' Network tab. */
suspend fun OkHttpClient.getXhr(
    url: HttpUrl,
    headers: Headers,
    cacheControl: CacheControl = DEFAULT_CACHE_CONTROL,
    ensureSuccess: Boolean = true,
): Response = send(Request.Builder().url(url).headers(headers).cacheControl(cacheControl).forXhr(), ensureSuccess)

/** GETs the way the page's own `fetch()`/XHR does: JSON APIs, ajax. Shows under Fetch/XHR in DevTools' Network tab. */
suspend fun OkHttpClient.getXhr(
    url: String,
    headers: Headers,
    cacheControl: CacheControl = DEFAULT_CACHE_CONTROL,
    ensureSuccess: Boolean = true,
): Response = getXhr(url.toHttpUrl(), headers, cacheControl, ensureSuccess)

/** GETs the way the page's own `fetch()`/XHR does: JSON APIs, ajax. Shows under Fetch/XHR in DevTools' Network tab. With the source's headers. */
context(source: HttpSource)
suspend fun OkHttpClient.getXhr(
    url: HttpUrl,
    cacheControl: CacheControl = DEFAULT_CACHE_CONTROL,
    ensureSuccess: Boolean = true,
): Response = getXhr(url, source.headers, cacheControl, ensureSuccess)

/** GETs the way the page's own `fetch()`/XHR does: JSON APIs, ajax. Shows under Fetch/XHR in DevTools' Network tab. With the source's headers. */
context(source: HttpSource)
suspend fun OkHttpClient.getXhr(
    url: String,
    cacheControl: CacheControl = DEFAULT_CACHE_CONTROL,
    ensureSuccess: Boolean = true,
): Response = getXhr(url.toHttpUrl(), source.headers, cacheControl, ensureSuccess)

/** GETs an image the way a browser's `<img>` does. Shows under Img in DevTools' Network tab. */
suspend fun OkHttpClient.getImage(
    url: HttpUrl,
    headers: Headers,
    cacheControl: CacheControl = DEFAULT_CACHE_CONTROL,
    ensureSuccess: Boolean = true,
): Response = send(Request.Builder().url(url).headers(headers).cacheControl(cacheControl).forImage(), ensureSuccess)

/** GETs an image the way a browser's `<img>` does. Shows under Img in DevTools' Network tab. */
suspend fun OkHttpClient.getImage(
    url: String,
    headers: Headers,
    cacheControl: CacheControl = DEFAULT_CACHE_CONTROL,
    ensureSuccess: Boolean = true,
): Response = getImage(url.toHttpUrl(), headers, cacheControl, ensureSuccess)

/** GETs an image the way a browser's `<img>` does. Shows under Img in DevTools' Network tab. With the source's headers. */
context(source: HttpSource)
suspend fun OkHttpClient.getImage(
    url: HttpUrl,
    cacheControl: CacheControl = DEFAULT_CACHE_CONTROL,
    ensureSuccess: Boolean = true,
): Response = getImage(url, source.headers, cacheControl, ensureSuccess)

/** GETs an image the way a browser's `<img>` does. Shows under Img in DevTools' Network tab. With the source's headers. */
context(source: HttpSource)
suspend fun OkHttpClient.getImage(
    url: String,
    cacheControl: CacheControl = DEFAULT_CACHE_CONTROL,
    ensureSuccess: Boolean = true,
): Response = getImage(url.toHttpUrl(), source.headers, cacheControl, ensureSuccess)

/** GETs a script the way a browser's `<script>` does. Shows under JS in DevTools' Network tab. */
suspend fun OkHttpClient.getScript(
    url: HttpUrl,
    headers: Headers,
    cacheControl: CacheControl = DEFAULT_CACHE_CONTROL,
    ensureSuccess: Boolean = true,
): Response = send(Request.Builder().url(url).headers(headers).cacheControl(cacheControl).forScript(), ensureSuccess)

/** GETs a script the way a browser's `<script>` does. Shows under JS in DevTools' Network tab. */
suspend fun OkHttpClient.getScript(
    url: String,
    headers: Headers,
    cacheControl: CacheControl = DEFAULT_CACHE_CONTROL,
    ensureSuccess: Boolean = true,
): Response = getScript(url.toHttpUrl(), headers, cacheControl, ensureSuccess)

/** GETs a script the way a browser's `<script>` does. Shows under JS in DevTools' Network tab. With the source's headers. */
context(source: HttpSource)
suspend fun OkHttpClient.getScript(
    url: HttpUrl,
    cacheControl: CacheControl = DEFAULT_CACHE_CONTROL,
    ensureSuccess: Boolean = true,
): Response = getScript(url, source.headers, cacheControl, ensureSuccess)

/** GETs a script the way a browser's `<script>` does. Shows under JS in DevTools' Network tab. With the source's headers. */
context(source: HttpSource)
suspend fun OkHttpClient.getScript(
    url: String,
    cacheControl: CacheControl = DEFAULT_CACHE_CONTROL,
    ensureSuccess: Boolean = true,
): Response = getScript(url.toHttpUrl(), source.headers, cacheControl, ensureSuccess)

/** GETs a stylesheet the way a browser's `<link rel=stylesheet>` does. Shows under CSS in DevTools' Network tab. */
suspend fun OkHttpClient.getStyle(
    url: HttpUrl,
    headers: Headers,
    cacheControl: CacheControl = DEFAULT_CACHE_CONTROL,
    ensureSuccess: Boolean = true,
): Response = send(Request.Builder().url(url).headers(headers).cacheControl(cacheControl).forStyle(), ensureSuccess)

/** GETs a stylesheet the way a browser's `<link rel=stylesheet>` does. Shows under CSS in DevTools' Network tab. */
suspend fun OkHttpClient.getStyle(
    url: String,
    headers: Headers,
    cacheControl: CacheControl = DEFAULT_CACHE_CONTROL,
    ensureSuccess: Boolean = true,
): Response = getStyle(url.toHttpUrl(), headers, cacheControl, ensureSuccess)

/** GETs a stylesheet the way a browser's `<link rel=stylesheet>` does. Shows under CSS in DevTools' Network tab. With the source's headers. */
context(source: HttpSource)
suspend fun OkHttpClient.getStyle(
    url: HttpUrl,
    cacheControl: CacheControl = DEFAULT_CACHE_CONTROL,
    ensureSuccess: Boolean = true,
): Response = getStyle(url, source.headers, cacheControl, ensureSuccess)

/** GETs a stylesheet the way a browser's `<link rel=stylesheet>` does. Shows under CSS in DevTools' Network tab. With the source's headers. */
context(source: HttpSource)
suspend fun OkHttpClient.getStyle(
    url: String,
    cacheControl: CacheControl = DEFAULT_CACHE_CONTROL,
    ensureSuccess: Boolean = true,
): Response = getStyle(url.toHttpUrl(), source.headers, cacheControl, ensureSuccess)

/** GETs audio the way a browser's `<audio>` does. Shows under Media in DevTools' Network tab. */
suspend fun OkHttpClient.getAudio(
    url: HttpUrl,
    headers: Headers,
    cacheControl: CacheControl = DEFAULT_CACHE_CONTROL,
    ensureSuccess: Boolean = true,
): Response = send(Request.Builder().url(url).headers(headers).cacheControl(cacheControl).forAudio(), ensureSuccess)

/** GETs audio the way a browser's `<audio>` does. Shows under Media in DevTools' Network tab. */
suspend fun OkHttpClient.getAudio(
    url: String,
    headers: Headers,
    cacheControl: CacheControl = DEFAULT_CACHE_CONTROL,
    ensureSuccess: Boolean = true,
): Response = getAudio(url.toHttpUrl(), headers, cacheControl, ensureSuccess)

/** GETs audio the way a browser's `<audio>` does. Shows under Media in DevTools' Network tab. With the source's headers. */
context(source: HttpSource)
suspend fun OkHttpClient.getAudio(
    url: HttpUrl,
    cacheControl: CacheControl = DEFAULT_CACHE_CONTROL,
    ensureSuccess: Boolean = true,
): Response = getAudio(url, source.headers, cacheControl, ensureSuccess)

/** GETs audio the way a browser's `<audio>` does. Shows under Media in DevTools' Network tab. With the source's headers. */
context(source: HttpSource)
suspend fun OkHttpClient.getAudio(
    url: String,
    cacheControl: CacheControl = DEFAULT_CACHE_CONTROL,
    ensureSuccess: Boolean = true,
): Response = getAudio(url.toHttpUrl(), source.headers, cacheControl, ensureSuccess)

/** GETs video the way a browser's `<video>` does. Shows under Media in DevTools' Network tab. */
suspend fun OkHttpClient.getVideo(
    url: HttpUrl,
    headers: Headers,
    cacheControl: CacheControl = DEFAULT_CACHE_CONTROL,
    ensureSuccess: Boolean = true,
): Response = send(Request.Builder().url(url).headers(headers).cacheControl(cacheControl).forVideo(), ensureSuccess)

/** GETs video the way a browser's `<video>` does. Shows under Media in DevTools' Network tab. */
suspend fun OkHttpClient.getVideo(
    url: String,
    headers: Headers,
    cacheControl: CacheControl = DEFAULT_CACHE_CONTROL,
    ensureSuccess: Boolean = true,
): Response = getVideo(url.toHttpUrl(), headers, cacheControl, ensureSuccess)

/** GETs video the way a browser's `<video>` does. Shows under Media in DevTools' Network tab. With the source's headers. */
context(source: HttpSource)
suspend fun OkHttpClient.getVideo(
    url: HttpUrl,
    cacheControl: CacheControl = DEFAULT_CACHE_CONTROL,
    ensureSuccess: Boolean = true,
): Response = getVideo(url, source.headers, cacheControl, ensureSuccess)

/** GETs video the way a browser's `<video>` does. Shows under Media in DevTools' Network tab. With the source's headers. */
context(source: HttpSource)
suspend fun OkHttpClient.getVideo(
    url: String,
    cacheControl: CacheControl = DEFAULT_CACHE_CONTROL,
    ensureSuccess: Boolean = true,
): Response = getVideo(url.toHttpUrl(), source.headers, cacheControl, ensureSuccess)

/** GETs as built, with the source's headers (KeiSource's default `Origin` included) and nothing a
 * browser adds. For a request the site rejects with browser headers. */
suspend fun OkHttpClient.getRaw(
    url: HttpUrl,
    headers: Headers,
    cacheControl: CacheControl = DEFAULT_CACHE_CONTROL,
    ensureSuccess: Boolean = true,
): Response = send(Request.Builder().url(url).headers(headers).cacheControl(cacheControl).raw(), ensureSuccess)

/** GETs without the headers a browser adds (client hints, fetch metadata, `Origin`, browser header order). */
suspend fun OkHttpClient.getRaw(
    url: String,
    headers: Headers,
    cacheControl: CacheControl = DEFAULT_CACHE_CONTROL,
    ensureSuccess: Boolean = true,
): Response = getRaw(url.toHttpUrl(), headers, cacheControl, ensureSuccess)

/** GETs without the headers a browser adds (client hints, fetch metadata, `Origin`, browser header order). With the source's headers. */
context(source: HttpSource)
suspend fun OkHttpClient.getRaw(
    url: HttpUrl,
    cacheControl: CacheControl = DEFAULT_CACHE_CONTROL,
    ensureSuccess: Boolean = true,
): Response = getRaw(url, source.headers, cacheControl, ensureSuccess)

/** GETs without the headers a browser adds (client hints, fetch metadata, `Origin`, browser header order). With the source's headers. */
context(source: HttpSource)
suspend fun OkHttpClient.getRaw(
    url: String,
    cacheControl: CacheControl = DEFAULT_CACHE_CONTROL,
    ensureSuccess: Boolean = true,
): Response = getRaw(url.toHttpUrl(), source.headers, cacheControl, ensureSuccess)

/** POSTs the way a browser submits a form. Shows under Doc in DevTools' Network tab. */
suspend fun OkHttpClient.postDocument(
    url: HttpUrl,
    headers: Headers,
    body: RequestBody,
    ensureSuccess: Boolean = true,
): Response = send(Request.Builder().url(url).headers(headers).post(body).forDocument(), ensureSuccess)

/** POSTs the way a browser submits a form. Shows under Doc in DevTools' Network tab. */
suspend fun OkHttpClient.postDocument(
    url: String,
    headers: Headers,
    body: RequestBody,
    ensureSuccess: Boolean = true,
): Response = postDocument(url.toHttpUrl(), headers, body, ensureSuccess)

/** POSTs the way a browser submits a form. Shows under Doc in DevTools' Network tab. With the source's headers. */
context(source: HttpSource)
suspend fun OkHttpClient.postDocument(
    url: HttpUrl,
    body: RequestBody,
    ensureSuccess: Boolean = true,
): Response = postDocument(url, source.headers, body, ensureSuccess)

/** POSTs the way a browser submits a form. Shows under Doc in DevTools' Network tab. With the source's headers. */
context(source: HttpSource)
suspend fun OkHttpClient.postDocument(
    url: String,
    body: RequestBody,
    ensureSuccess: Boolean = true,
): Response = postDocument(url.toHttpUrl(), source.headers, body, ensureSuccess)

/** POSTs the way the page's own `fetch()`/XHR does: JSON APIs, ajax. Shows under Fetch/XHR in DevTools' Network tab. */
suspend fun OkHttpClient.postXhr(
    url: HttpUrl,
    headers: Headers,
    body: RequestBody,
    ensureSuccess: Boolean = true,
): Response = send(Request.Builder().url(url).headers(headers).post(body).forXhr(), ensureSuccess)

/** POSTs the way the page's own `fetch()`/XHR does: JSON APIs, ajax. Shows under Fetch/XHR in DevTools' Network tab. */
suspend fun OkHttpClient.postXhr(
    url: String,
    headers: Headers,
    body: RequestBody,
    ensureSuccess: Boolean = true,
): Response = postXhr(url.toHttpUrl(), headers, body, ensureSuccess)

/** POSTs the way the page's own `fetch()`/XHR does: JSON APIs, ajax. Shows under Fetch/XHR in DevTools' Network tab. With the source's headers. */
context(source: HttpSource)
suspend fun OkHttpClient.postXhr(
    url: HttpUrl,
    body: RequestBody,
    ensureSuccess: Boolean = true,
): Response = postXhr(url, source.headers, body, ensureSuccess)

/** POSTs the way the page's own `fetch()`/XHR does: JSON APIs, ajax. Shows under Fetch/XHR in DevTools' Network tab. With the source's headers. */
context(source: HttpSource)
suspend fun OkHttpClient.postXhr(
    url: String,
    body: RequestBody,
    ensureSuccess: Boolean = true,
): Response = postXhr(url.toHttpUrl(), source.headers, body, ensureSuccess)

/** POSTs as built, with the source's headers (KeiSource's default `Origin` included) and nothing a
 * browser adds. For a request the site rejects with browser headers. */
suspend fun OkHttpClient.postRaw(
    url: HttpUrl,
    headers: Headers,
    body: RequestBody,
    ensureSuccess: Boolean = true,
): Response = send(Request.Builder().url(url).headers(headers).post(body).raw(), ensureSuccess)

/** POSTs without the headers a browser adds (client hints, fetch metadata, `Origin`, browser header order). */
suspend fun OkHttpClient.postRaw(
    url: String,
    headers: Headers,
    body: RequestBody,
    ensureSuccess: Boolean = true,
): Response = postRaw(url.toHttpUrl(), headers, body, ensureSuccess)

/** POSTs without the headers a browser adds (client hints, fetch metadata, `Origin`, browser header order). With the source's headers. */
context(source: HttpSource)
suspend fun OkHttpClient.postRaw(
    url: HttpUrl,
    body: RequestBody,
    ensureSuccess: Boolean = true,
): Response = postRaw(url, source.headers, body, ensureSuccess)

/** POSTs without the headers a browser adds (client hints, fetch metadata, `Origin`, browser header order). With the source's headers. */
context(source: HttpSource)
suspend fun OkHttpClient.postRaw(
    url: String,
    body: RequestBody,
    ensureSuccess: Boolean = true,
): Response = postRaw(url.toHttpUrl(), source.headers, body, ensureSuccess)

private suspend fun OkHttpClient.send(request: Request.Builder, ensureSuccess: Boolean): Response {
    val call = newCall(request.build())
    return if (ensureSuccess) call.awaitSuccess() else call.await()
}
