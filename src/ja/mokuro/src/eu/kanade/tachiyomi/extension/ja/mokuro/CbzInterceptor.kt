package eu.kanade.tachiyomi.extension.ja.mokuro

import keiyoushi.utils.parseAs
import keiyoushi.zip.dataRange
import keiyoushi.zip.range
import keiyoushi.zip.readEntry
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.buffer

class CbzInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val fragment = request.url.fragment

        if (fragment == null || !request.url.pathSegments.last().endsWith(".cbz", true)) {
            return chain.proceed(request)
        }

        val data = fragment.parseAs<ImageRequest>()
        val baseUrl = request.url.newBuilder().fragment(null).build()
        val range = dataRange(data.offset, data.compressedSize)

        val rangeRequest = request.newBuilder()
            .url(baseUrl)
            .range(range)
            .build()

        val response = chain.proceed(rangeRequest)
        if (!response.isSuccessful) return response
        val image = readEntry(response.body.source(), data.compressedSize, data.method).buffer()

        return response.newBuilder()
            .removeHeader("Content-Range")
            .removeHeader("Content-Length")
            .code(200)
            .message("OK")
            .body(image.asResponseBody(getContentType(data.name).toMediaType()))
            .build()
    }

    private fun getContentType(name: String): String {
        val lower = name.lowercase()
        return when {
            lower.endsWith(".webp") -> "image/webp"
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
            lower.endsWith(".png") -> "image/png"
            else -> "application/octet-stream"
        }
    }
}
