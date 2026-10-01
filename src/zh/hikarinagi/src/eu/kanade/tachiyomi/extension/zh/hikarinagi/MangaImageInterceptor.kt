package eu.kanade.tachiyomi.extension.zh.hikarinagi

import android.util.Base64
import keiyoushi.utils.toJsonRequestBody
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException

/**
 * Serves a manga page, which the site only hands out over a POST carrying a token the client
 * generated itself. The answer is encrypted with that token, see [ReaderCrypto].
 */
class MangaImageInterceptor(private val baseUrl: String) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url
        if (url.host != HOST) return chain.proceed(request)

        val (mangaId, chapterId, pageId) = url.pathSegments
        val token = ReaderCrypto.newToken()
        val contentRequest = request.newBuilder()
            .url("$baseUrl/api/v3/reader/mangas/$mangaId/chapters/$chapterId/pages/$pageId/content")
            .post(buildJsonObject { put("p", Base64.encodeToString(token, TOKEN_ENCODING)) }.toJsonRequestBody())
            .build()

        val response = chain.proceed(contentRequest)
        if (!response.isSuccessful) {
            val code = response.code
            response.close()
            throw IOException(if (code == 401) "请先在 WebView 中登录" else "加载图片失败（HTTP $code）")
        }

        val page = response.use { ReaderCrypto.decrypt(token, it.body.bytes(), "manga:page:$mangaId:$chapterId:$pageId") }
        return Response.Builder().request(request).ok(page.toResponseBody((url.queryParameter("mime") ?: "image/jpeg").toMediaType()))
    }

    companion object {
        private const val HOST = "hikarinagi-manga-image"

        /** The token travels as unpadded base64url. */
        private val TOKEN_ENCODING = Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP

        /** Where the reader loads a page from; [mimeType] only labels the decrypted bytes. */
        fun createUrl(mangaId: String, chapterId: String, pageId: String, mimeType: String?): String = "http://$HOST/$mangaId/$chapterId/$pageId" + (mimeType?.let { "?mime=$it" } ?: "")
    }
}

/** The 200 the local page interceptors answer with. */
private fun Response.Builder.ok(body: ResponseBody): Response = code(200).message("OK").protocol(Protocol.HTTP_2).body(body).build()
