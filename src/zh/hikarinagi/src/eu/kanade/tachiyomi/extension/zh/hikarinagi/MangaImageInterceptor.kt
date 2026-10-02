package eu.kanade.tachiyomi.extension.zh.hikarinagi

import android.util.Base64
import keiyoushi.utils.toJsonRequestBody
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.buffer
import okio.cipherSource
import java.io.IOException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Serves a manga page. The site answers one with a POST whose body carries a token the client
 * generated itself, and the answer is `iv || AES-256-GCM(ciphertext)` keyed by that token.
 *
 * The page list hands the reader the site's own content URL, so the browser-side request is the
 * one a mirror change is reflected in; this turns its GET into that POST and hands the decrypted
 * bytes back. The type they are labelled with rides in the fragment, where the site never sees it.
 */
class MangaImageInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url
        // Only the reader's pages carry a fragment, and it is the type of the bytes to serve.
        val mime = url.fragment ?: return chain.proceed(request)
        // .../api/v3/reader/mangas/<mangaId>/chapters/<chapterId>/pages/<pageId>/content
        val (mangaId, _, chapterId, _, pageId) = url.pathSegments.drop(4)

        val token = newToken()
        val contentRequest = request.newBuilder()
            .post(buildJsonObject { put("p", Base64.encodeToString(token, TOKEN_ENCODING)) }.toJsonRequestBody())
            .build()

        val response = chain.proceed(contentRequest)
        if (!response.isSuccessful) {
            response.close()
            throw IOException(if (response.code == 401) "请先在 WebView 中登录" else "加载图片失败（HTTP ${response.code}）")
        }

        // The answer is decrypted while the reader reads it, so a page is never held whole in memory.
        val source = response.body.source()
        val cipher = newCipher(token, source.readByteArray(IV_SIZE.toLong()), "manga:page:$mangaId:$chapterId:$pageId")
        val contentLength = response.body.contentLength().takeIf { it > 0 }?.minus(IV_SIZE + TAG_BITS / 8) ?: -1L
        val page = source.cipherSource(cipher).buffer().asResponseBody(mime.toMediaType(), contentLength)

        return Response.Builder().request(request).code(200).message("OK").protocol(Protocol.HTTP_2).body(page).build()
    }

    /** The token a page request carries; the site encrypts its answer with it. */
    private fun newToken(): ByteArray = ByteArray(TOKEN_SIZE).also(random::nextBytes)

    /** The cipher the answer is read through; the site puts its iv in front of the ciphertext. */
    private fun newCipher(token: ByteArray, iv: ByteArray, associatedData: String): Cipher {
        // The key is the token's two halves XORed together.
        val key = ByteArray(TOKEN_SIZE / 2) { (token[it].toInt() xor token[it + TOKEN_SIZE / 2].toInt()).toByte() }
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
            updateAAD(associatedData.toByteArray(Charsets.UTF_8))
        }
    }

    companion object {
        /** The token travels as unpadded base64url. */
        private const val TOKEN_ENCODING = Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP

        private const val TOKEN_SIZE = 64
        private const val IV_SIZE = 12
        private const val TAG_BITS = 128

        private val random = SecureRandom()

        /**
         * Where the reader loads a page from: the site's own content URL, with the type it should be
         * labelled with in the fragment. Built here so it follows the mirror [Hikarinagi.baseUrl].
         */
        fun createUrl(baseUrl: String, mangaId: String, chapterId: String, pageId: String, mimeType: String): String = "$baseUrl/api/v3/reader/mangas/$mangaId/chapters/$chapterId/pages/$pageId/content" + "#$mimeType"
    }
}
