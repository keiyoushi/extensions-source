package eu.kanade.tachiyomi.extension.en.asmotoon.waybackmachineinterceptor

import android.util.Log
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import okio.EOFException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

class WaybackMachineInterceptor(
    private val include: Regex = ".*".toRegex(),
) : Interceptor {
    // LinkedHashMap with a capacity of URL_CACHE_MAX_ENTRIES. When exceeding the capacity the oldest entry is removed.
    private val urlCache = object : LinkedHashMap<HttpUrl, HttpUrl>() {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<HttpUrl, HttpUrl>?,
        ): Boolean = size > URL_CACHE_MAX_ENTRIES
    }

    /**
     * Get a timestamp from a Wayback Machine URL without a timestamp
     */
    private fun getTimestamp(chain: Interceptor.Chain, archiveUrl: HttpUrl): String? = chain.proceed(
        chain
            .request()
            .newBuilder()
            .url(archiveUrl)
            .build(),
    ).use {
        it.header("Location")?.substring(WEB_PREFIX.length, WEB_PREFIX.length + 14)
    }

    /**
     * Get the "id_" url, which points to the raw, unmodified content
     */
    private fun getSnapshotUrl(
        timestamp: String,
        url: HttpUrl,
    ): HttpUrl = "$WEB_PREFIX${timestamp}id_/$url".toHttpUrl()

    /**
     * Create a new URL to retry the snapshot
     */
    private fun getRetryUrl(url: HttpUrl): HttpUrl = url
        .newBuilder()
        .setQueryParameter(RANDOM_QUERY_PARAM, UUID.randomUUID().toString())
        .build()

    /**
     * Gets the response from the Wayback Machine without following redirects
     */
    private fun getImmediateResponse(
        chain: Interceptor.Chain,
        url: HttpUrl,
    ): Response = chain.proceed(
        chain.request().newBuilder().url(
            urlCache[url] ?: if (url.host == HOST || !include.matches(url.toString())) {
                // url is a Wayback Machine URL or isn't matched, do nothing
                url
            } else {
                getTimestamp(chain, "$WEB_PREFIX$url".toHttpUrl())?.let { timestamp ->
                    if (System.currentTimeMillis() - DATE_FORMAT.parse(timestamp)!!.time > SNAPSHOT_MAX_AGE_MS) {
                        // snapshot is older than SNAPSHOT_MAX_AGE_MS, attempt to create a new snapshot
                        snapshot(chain, url) ?: getSnapshotUrl(timestamp, url)
                    } else {
                        // snapshot is recent
                        getSnapshotUrl(timestamp, url)
                    }
                }

                    // snapshot doesn't exist, create a new snapshot
                    ?: snapshot(chain, url)

                    // archiving failed
                    ?: throw Exception("Failed to archive page")
            },
        ).build(),
    )

    override fun intercept(chain: Interceptor.Chain): Response {
        val url = chain.request().url
        var response = getImmediateResponse(chain, url)

        // resolve all redirects
        while (response.isRedirect) {
            val newUrl = response.header("Location")?.toHttpUrl() ?: break
            response.close()
            response = getImmediateResponse(chain, newUrl)
        }

        // Cache the url
        urlCache[url] = response.request.url

        if (response.request.url.host == HOST && response.body.contentType()?.type == "text") {
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
                    }.asResponseBody(response.body.contentType()),
                ).build()
            }
        }

        return response
    }

    /**
     * Creates a snapshot and returns the snapshot URL
     */
    private fun snapshot(
        chain: Interceptor.Chain,
        url: HttpUrl,
    ): HttpUrl? = getTimestamp(chain, "$SAVE_PREFIX$url".toHttpUrl())?.let { timestamp ->
        getSnapshotUrl(timestamp, url)
    } ?: getRetryUrl(url).let { retryUrl ->
        // Retry snapshot with a new URL
        getTimestamp(chain, "$SAVE_PREFIX$retryUrl".toHttpUrl())?.let { timestamp ->
            getSnapshotUrl(timestamp, retryUrl)
        }
    }

    companion object {
        private const val HOST = "web.archive.org"
        private const val SAVE_PREFIX = "https://$HOST/save/"
        private const val WEB_PREFIX = "https://$HOST/web/"
        private const val RANDOM_QUERY_PARAM = "__WaybackMachineInterceptor_RANDOM_QUERY_PARAM__"
        private const val SNAPSHOT_MAX_AGE_MS = 24 * 60 * 60 * 1000
        private const val URL_CACHE_MAX_ENTRIES = 250
        private val DATE_FORMAT = SimpleDateFormat("yyyyMMddHHmmss", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }
}
