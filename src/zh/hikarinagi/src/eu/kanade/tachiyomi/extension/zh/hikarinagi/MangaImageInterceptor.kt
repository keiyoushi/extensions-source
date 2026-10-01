package eu.kanade.tachiyomi.extension.zh.hikarinagi

import android.util.Base64
import keiyoushi.utils.toJsonRequestBody
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Serves a manga page. The site no longer hands out image URLs: a content request is a POST carrying
 * a token the client generated itself, and the answer is `iv || AES-256-GCM(ciphertext)` keyed by
 * that token, so the bytes have to be decrypted here before the reader ever sees them.
 */
class MangaImageInterceptor(private val baseUrl: String) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url
        if (url.host != HOST) return chain.proceed(request)

        val (mangaId, chapterId, pageId) = url.pathSegments
        val token = newToken()
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

        val page = response.use { decrypt(token, it.body.bytes(), "manga:page:$mangaId:$chapterId:$pageId") }
        return Response.Builder().request(request).code(200).message("OK").protocol(Protocol.HTTP_2)
            .body(page.toResponseBody((url.queryParameter("mime") ?: "image/jpeg").toMediaType())).build()
    }

    /** The token a page request carries; the site encrypts its answer with it. */
    private fun newToken(): ByteArray = ByteArray(TOKEN_SIZE).also(random::nextBytes)

    /** Decrypts a page the site bound to [associatedData] with the [token] that requested it. */
    private fun decrypt(token: ByteArray, content: ByteArray, associatedData: String): ByteArray = try {
        // The key is the token's two halves XORed together.
        val key = ByteArray(TOKEN_SIZE / 2) { (token[it].toInt() xor token[it + TOKEN_SIZE / 2].toInt()).toByte() }
        Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, content, 0, IV_SIZE))
            updateAAD(associatedData.toByteArray(Charsets.UTF_8))
            doFinal(content, IV_SIZE, content.size - IV_SIZE)
        }
    } catch (e: Exception) {
        throw IOException(DECRYPT_FAILED, e)
    }

    companion object {
        private const val HOST = "hikarinagi-manga-image"

        /** The token travels as unpadded base64url. */
        private const val TOKEN_ENCODING = Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP

        private const val TOKEN_SIZE = 64
        private const val IV_SIZE = 12
        private const val TAG_BITS = 128
        private const val DECRYPT_FAILED = "内容解密失败，请刷新后重试"

        private val random = SecureRandom()

        /** Where the reader loads a page from; [mimeType] only labels the decrypted bytes. */
        fun createUrl(mangaId: String, chapterId: String, pageId: String, mimeType: String?): String = "http://$HOST/$mangaId/$chapterId/$pageId" + (mimeType?.let { "?mime=$it" } ?: "")
    }
}
