package eu.kanade.tachiyomi.extension.en.asmotoon.waybackmachineinterceptor

import android.util.Log
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import okio.EOFException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

class WaybackMachineInterceptor(
    private val regex: Regex = ".*".toRegex(),
) : Interceptor {
    // LinkedHashMap with a capacity of 250. When exceeding the capacity the oldest entry is removed.
    private val urlCache = object : LinkedHashMap<HttpUrl, HttpUrl>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<HttpUrl, HttpUrl>?): Boolean = size > 250
    }

    /**
     * Gets the response from the Wayback Machine without following redirects
     */
    private fun getImmediateResponse(
        chain: Interceptor.Chain,
        request: Request,
    ): Response = urlCache[request.url]?.let {
        // url is cached, use cached url
        chain.proceed(request.newBuilder().url(it).build())
    } ?: request.url.let { url ->
        if (url.host == HOST) {
            // url is a Wayback Machine URL, do nothing
            chain.proceed(request)
        } else {
            val (dateStr, newUrl) = getDateStr(chain, "$WEB_PREFIX$url")?.let { dateStr ->
                val date = DATE_FORMAT.parse(dateStr)!!
                if (System.currentTimeMillis() - date.time > 24 * 60 * 60 * 1000) {
                    // archive is older than 24 hours, create a new archive
                    archiveUrl(chain, url) ?: Pair(dateStr, url)
                } else {
                    // archive is recent
                    Pair(dateStr, url)
                }
            }

                // archive doesn't exist, create a new archive
                ?: archiveUrl(chain, url)

                // archiving failed
                ?: throw Exception("Failed to archive page")

            // use the "id_" url, which points to the raw, unmodified content
            val finalUrl = "$WEB_PREFIX${dateStr}id_/$newUrl".toHttpUrl()
            chain.proceed(request.newBuilder().url(finalUrl).build())
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        if (!regex.matches(request.url.toString())) {
            // url does not match regex, do nothing
            return chain.proceed(request)
        }

        var response = getImmediateResponse(chain, request)

        // resolve all redirects
        while (response.isRedirect) {
            response = response.use { response ->
                getImmediateResponse(
                    chain,
                    response
                        .request
                        .newBuilder()
                        .url(response.request.header("Location")!!)
                        .build(),
                )
            }
        }

        // Cache the url
        urlCache[request.url] = response.request.url

        if (response.body.contentType()?.type == "text") {
            // Sometimes, the response is truncated. This prevents an EOFException
            response = response.use { response ->
                response.newBuilder().headers(
                    response.headers.newBuilder()
                        .removeAll("Content-Encoding")
                        .removeAll("Content-Length")
                        .build(),
                ).body(
                    Buffer().also {
                        val stream = response.body.byteStream()
                        val out = it.outputStream()
                        val buf = ByteArray(8192)
                        while (true) {
                            try {
                                val len = stream.read(buf)
                                if (len <= 0) break
                                out.write(buf, 0, len)
                            } catch (_: EOFException) {
                                Log.e("WaybackMachine", "Response truncated")
                                break
                            }
                        }
                    }.asResponseBody(response.header("Content-Type")?.toMediaType()),
                ).build()
            }
        }

        return response
    }

    private fun archiveUrl(
        chain: Interceptor.Chain,
        url: HttpUrl,
    ): Pair<String, HttpUrl>? = getDateStr(chain, "$SAVE_PREFIX$url")?.let { dateStr ->
        Pair(dateStr, url)
    } ?: getRetryUrl(url).let { retryUrl ->
        // Retry archive with a new URL
        getDateStr(chain, "$SAVE_PREFIX$retryUrl")?.let { dateStr ->
            Pair(dateStr, retryUrl)
        }
    }

    companion object {
        private fun getDateStr(chain: Interceptor.Chain, archiveUrl: String): String? = chain.proceed(
            chain
                .request()
                .newBuilder()
                .url(archiveUrl)
                .build(),
        ).use {
            it.header("Location")?.substring(WEB_PREFIX.length, WEB_PREFIX.length + 14)
        }

        private fun getRetryUrl(url: HttpUrl): HttpUrl = url
            .newBuilder()
            .setQueryParameter(RANDOM_QUERY_PARAM, UUID.randomUUID().toString())
            .build()

        private const val HOST = "web.archive.org"
        private const val SAVE_PREFIX = "https://$HOST/save/"
        private const val WEB_PREFIX = "https://$HOST/web/"
        private const val RANDOM_QUERY_PARAM = "__WaybackMachineInterceptor_RANDOM_QUERY_PARAM__"
        private val DATE_FORMAT = SimpleDateFormat("yyyyMMddHHmmss", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }
}
