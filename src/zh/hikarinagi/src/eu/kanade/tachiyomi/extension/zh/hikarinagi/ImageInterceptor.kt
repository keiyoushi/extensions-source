package eu.kanade.tachiyomi.extension.zh.hikarinagi

import android.util.Base64
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
 * Serves a manga page. The site no longer hands out image URLs: it wants a POST whose body carries
 * a token the client generated itself, and it answers with `iv || AES-256-GCM(ciphertext)` keyed by
 * that token.
 *
 * The request is built by `Hikarinagi.imageRequest`, so this only has to decrypt what comes back.
 * The token it needs for that rides in the request fragment, where the site never sees it.
 */
class ImageInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        // Only the reader's pages carry a fragment, and it is `<type>|<token>`.
        val fragment = request.url.fragment ?: return chain.proceed(request)
        val mime = fragment.substringBefore('|')
        val token = Base64.decode(fragment.substringAfter('|'), TOKEN_ENCODING)
        // .../api/v3/reader/mangas/<mid>/chapters/<cid>/pages/<pid>/content
        val (mid, _, cid, _, pid) = request.url.pathSegments.drop(4)

        val response = chain.proceed(request)
        if (!response.isSuccessful) {
            response.close()
            throw IOException(if (response.code == 401) "请先在 WebView 中登录" else "加载图片失败（HTTP ${response.code}）")
        }

        // The answer is decrypted while the reader reads it, so a page is never held whole in memory.
        val source = response.body.source()
        val cipher = newCipher(token, source.readByteArray(IV_SIZE.toLong()), "manga:page:$mid:$cid:$pid")
        val contentLength = response.body.contentLength().takeIf { it > 0 }?.minus(IV_SIZE + TAG_BITS / 8) ?: -1L
        val page = source.cipherSource(cipher).buffer().asResponseBody(mime.toMediaType(), contentLength)

        return Response.Builder().request(request).code(200).message("OK").protocol(Protocol.HTTP_2).body(page).build()
    }

    /** The cipher the answer is read through; the site puts it's iv in front of the ciphertext. */
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

        /** The token a page request carries: the site encrypts its answer with it, the fragment hands it back. */
        fun newToken(): String = Base64.encodeToString(ByteArray(TOKEN_SIZE).also(random::nextBytes), TOKEN_ENCODING)
    }
}
