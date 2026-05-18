package eu.kanade.tachiyomi.extension.en.asmotoon.waybackmachineinterceptor

import android.util.Log
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import okio.EOFException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class WaybackMachineInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        val url = request.url
        if (url.host != HOST) {
            var dateStr = getDateStr(chain, "$WEB_PREFIX$url")
            if (dateStr == null) {
                dateStr = getDateStr(chain, "$SAVE_PREFIX$url") ?: throw Exception("Failed to archive page")
            } else {
                val date = DATE_FORMAT.parse(dateStr)!!
                if (System.currentTimeMillis() - date.time > 24 * 60 * 60 * 1000) {
                    dateStr = getDateStr(chain, "$SAVE_PREFIX$url") ?: dateStr
                }
            }
            request = request
                .newBuilder()
                .url("$WEB_PREFIX${dateStr}id_/$url")
                .build()
        }

        var response = chain.proceed(request)

        while (response.isRedirect) {
            response = response.use {
                chain.proceed(
                    request
                        .newBuilder()
                        .url(it.header("Location")!!)
                        .build(),
                )
            }
        }

        // Sometimes, the response is truncated. This prevents an EOFException
        if (response.request.url.host == HOST) {
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

    companion object {
        private fun getDateStr(chain: Interceptor.Chain, url: String): String? = chain.proceed(
            chain
                .request()
                .newBuilder()
                .url(url)
                .build(),
        ).use {
            it.header("Location")?.substring(WEB_PREFIX.length, WEB_PREFIX.length + 14)
        }

        private const val HOST = "web.archive.org"
        private const val SAVE_PREFIX = "https://$HOST/save/"
        private const val WEB_PREFIX = "https://$HOST/web/"
        private val DATE_FORMAT = SimpleDateFormat("yyyyMMddHHmmss", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }
}
