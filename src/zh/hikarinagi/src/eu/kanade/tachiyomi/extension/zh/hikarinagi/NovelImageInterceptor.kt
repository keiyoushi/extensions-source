package eu.kanade.tachiyomi.extension.zh.hikarinagi

import keiyoushi.utils.applicationContext
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.buffer
import okio.source
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * Serves the illustrations extracted from a volume EPUB.
 *
 * Images are content addressed (`<sha1>.<extension>`), so page URLs stay short and stable even
 * though the same file name can appear in several volumes. The bytes are written to the app
 * cache while the page list is built.
 */
class NovelImageInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url
        if (url.host != HOST) return chain.proceed(request)

        val file = File(cacheDir, url.pathSegments.last())
        if (!file.exists()) throw IOException("插图已失效，请刷新本章节")

        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(file.source().buffer().asResponseBody(file.mediaType(), file.length()))
            .build()
    }

    companion object {
        private const val HOST = "hikarinagi-novel-image"

        private val cacheDir: File get() = applicationContext.cacheDir.resolve("novel-images")

        /** Stores an illustration and returns the URL it is served from. */
        fun save(bytes: ByteArray, extension: String): String {
            val name = "${bytes.digest()}.${extension.lowercase()}"
            val file = File(cacheDir, name)
            if (file.length() != bytes.size.toLong()) {
                cacheDir.mkdirs()
                file.writeBytes(bytes)
            }
            return "http://$HOST/$name"
        }

        private fun ByteArray.digest(): String = MessageDigest.getInstance("SHA-1").digest(this).take(12).joinToString("") { "%02x".format(it) }

        private fun File.mediaType(): MediaType = when (extension.lowercase()) {
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            else -> "image/jpeg"
        }.toMediaType()
    }
}
