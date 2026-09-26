package eu.kanade.tachiyomi.extension.zh.hikarinagi

import android.webkit.MimeTypeMap
import keiyoushi.utils.applicationContext
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.ByteString.Companion.toByteString
import okio.buffer
import okio.source
import java.io.File
import java.io.IOException

/** Serves the illustrations extracted from a volume EPUB, addressed by content. */
class NovelImageInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url
        if (url.host != HOST) return chain.proceed(request)

        val file = File(cacheDir, url.pathSegments.last())
        if (!file.exists()) throw IOException("插图已失效，请刷新本章节")

        return Response.Builder().request(request).ok(file.source().buffer().asResponseBody(file.mediaType(), file.length()))
    }

    companion object {
        private const val HOST = "hikarinagi-novel-image"

        private val cacheDir: File get() = applicationContext.cacheDir.resolve("novel-images")

        /** Stores an illustration and returns the URL it is served from. */
        fun save(bytes: ByteArray, extension: String): String {
            val name = "${bytes.toByteString().sha1().hex().take(24)}.${extension.lowercase()}"
            val file = File(cacheDir, name)
            if (file.length() != bytes.size.toLong()) {
                cacheDir.mkdirs()
                file.writeBytes(bytes)
            }
            return "http://$HOST/$name"
        }

        private fun File.mediaType(): MediaType = (MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase()) ?: "image/jpeg").toMediaType()
    }
}

/** The 200 the local page interceptors answer with. */
internal fun Response.Builder.ok(body: ResponseBody): Response = code(200).message("OK").protocol(Protocol.HTTP_2).body(body).build()
