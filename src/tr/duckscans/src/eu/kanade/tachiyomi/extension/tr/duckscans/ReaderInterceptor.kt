package eu.kanade.tachiyomi.extension.tr.duckscans

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import keiyoushi.lib.ece.Ece
import keiyoushi.utils.aesDecrypt
import keiyoushi.utils.aesGcm
import keiyoushi.utils.parseAs
import keiyoushi.utils.sha256
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import javax.crypto.KeyAgreement
import kotlin.math.abs
import kotlin.time.Clock

/** Tags a page request with the chapter page it belongs to. */
class ReaderPage(val chapterUrl: String)

// Pages come encrypted for our P-256 key, like in the site's reader worker, and cut into shuffled strips
class ReaderInterceptor : Interceptor {

    private val readerKey by lazy { ReaderKey() }

    private var freshPages: FreshPages? = null

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val chapterUrl = request.tag(ReaderPage::class.java)?.chapterUrl ?: return chain.proceed(request)

        val response = proceedEncrypted(chain, request)
        if (response.code != 403 || response.header("x-token-reason") != "expired") return decode(response)
        response.close()

        // Page addresses expire a few minutes after the chapter was opened: the site asks for new ones
        val freshUrl = synchronized(this) { freshUrl(chain, request, chapterUrl) } ?: request.url
        return decode(proceedEncrypted(chain, request.newBuilder().url(freshUrl).build()))
    }

    private fun proceedEncrypted(chain: Interceptor.Chain, request: Request): Response {
        val url = request.url.newBuilder()
            .setQueryParameter("cv", "5")
            .setQueryParameter("rk", readerKey.cacheKey)
            .build()
        return chain.proceed(
            request.newBuilder()
                .url(url)
                .header("X-Duck-Scramble-Version", "5")
                .header("X-Duck-Reader-Crypto", "1")
                .header("X-Duck-Reader-Key", readerKey.publicKey)
                .build(),
        )
    }

    private fun decode(response: Response): Response {
        if (!response.isSuccessful) return response

        val image = try {
            decrypt(response)
        } catch (e: GeneralSecurityException) {
            throw IOException("Could not decrypt the page", e)
        }
        val bitmap = BitmapFactory.decodeByteArray(image, 0, image.size) ?: throw IOException("Could not decode the page")
        val url = response.request.url
        val seed = when (response.header("x-duck-scramble-mode")) {
            "legacy" -> url.queryParameter("s")
            else -> url.queryParameter("k") ?: url.queryParameter("s")
        }
        val gutter = if (response.header("x-duck-scramble-version") == "5") 4 else 0
        val result = seed?.let { unscramble(bitmap, it, gutter).also { bitmap.recycle() } } ?: bitmap

        val buffer = Buffer()
        result.compress(Bitmap.CompressFormat.JPEG, 90, buffer.outputStream())
        result.recycle()
        return response.newBuilder()
            .body(buffer.asResponseBody(JPEG_MEDIA_TYPE, buffer.size))
            .build()
    }

    private fun decrypt(response: Response): ByteArray {
        fun header(name: String) = BASE64_URL.decode(response.header(name) ?: throw IOException("Missing $name"))

        val sharedSecret = KeyAgreement.getInstance("ECDH").run {
            init(readerKey.keyPair.private)
            doPhase(Ece.rawToPublicKey(header("x-duck-server-key")), true)
            generateSecret()
        }
        val wrappingKey = Ece.hkdf(sharedSecret, header("x-duck-key-salt"), WRAP_INFO, 32)
        val pageKey = header("x-duck-wrapped-key").aesDecrypt(
            aesGcm(wrappingKey, header("x-duck-key-iv")).apply { updateAAD(header("x-duck-wrap-aad")) },
        )

        val body = response.body.bytes()
        if (body.size < 32 || !body.copyOfRange(0, 4).contentEquals(PAYLOAD_MAGIC)) throw IOException("Invalid encrypted page")
        return body.copyOfRange(16, body.size).aesDecrypt(
            aesGcm(pageKey, body.copyOfRange(4, 16)).apply { updateAAD(header("x-duck-content-aad")) },
        )
    }

    // The seed gives the number of strips and their order; each strip has `gutter` spare rows above and below
    private fun unscramble(bitmap: Bitmap, seed: String, gutter: Int): Bitmap {
        val hash = abs(seedHash(seed))
        val pieces = 8 + (hash % 11).toInt()
        val height = bitmap.height - 2 * gutter * pieces
        if (height < pieces) throw IOException("Scrambled page is smaller than its strips")

        val order = IntArray(pieces) { it }
        var n = hash
        for (i in pieces - 1 downTo 1) {
            n = (9301 * n + 49297) % 233280
            val j = (n % (i + 1)).toInt()
            order[i] = order[j].also { order[j] = order[i] }
        }

        val pieceHeight = height / pieces
        val heights = IntArray(pieces) { if (it == pieces - 1) height - it * pieceHeight else pieceHeight }
        val sourceTops = IntArray(pieces)
        var top = gutter
        for (piece in order) {
            sourceTops[piece] = top
            top += heights[piece] + 2 * gutter
        }

        val result = Bitmap.createBitmap(bitmap.width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        for (piece in 0 until pieces) {
            val src = Rect(0, sourceTops[piece], bitmap.width, sourceTops[piece] + heights[piece])
            val dst = Rect(0, piece * pieceHeight, bitmap.width, piece * pieceHeight + heights[piece])
            canvas.drawBitmap(bitmap, src, dst, null)
        }
        return result
    }

    // JavaScript's `hash = c + ((hash << 5) - hash)`, where only the shift wraps to 32 bits
    private fun seedHash(seed: String): Long {
        var hash = 0L
        for (c in seed) hash = c.code + ((hash.toInt() shl 5).toLong() - hash)
        return hash
    }

    private fun freshUrl(chain: Interceptor.Chain, request: Request, chapterUrl: String): HttpUrl? {
        val now = Clock.System.now().toEpochMilliseconds()
        val urls = freshPages?.takeIf { it.chapterUrl == chapterUrl && it.until > now }?.urls
            ?: fetchPageUrls(chain, request, chapterUrl)
                .takeIf { it.isNotEmpty() }
                ?.also { freshPages = FreshPages(chapterUrl, it, now + FRESH_MILLIS) }
        return urls?.get(request.url.encodedPath)
    }

    private fun fetchPageUrls(chain: Interceptor.Chain, request: Request, chapterUrl: String): Map<String, HttpUrl> {
        val (_, series, _, chapter) = chapterUrl.toHttpUrl().pathSegments
        val apiUrl = request.url.newBuilder()
            .encodedPath("/api/chapters")
            .query(null)
            .addQueryParameter("series", series)
            .addQueryParameter("chapter", chapter)
            .addQueryParameter("includepages", "true")
            .addQueryParameter("limit", "1")
            .build()
        val response = chain.proceed(request.newBuilder().url(apiUrl).removeHeader("Accept").build())
        if (!response.isSuccessful) {
            response.close()
            return emptyMap()
        }
        return response.parseAs<ChapterListDto>().data.firstOrNull()?.pages.orEmpty()
            .mapNotNull { request.url.resolve(it) }
            .associateBy { it.encodedPath }
    }

    private class ReaderKey {
        val keyPair: KeyPair = KeyPairGenerator.getInstance("EC").run {
            initialize(ECGenParameterSpec("secp256r1"))
            generateKeyPair()
        }
        private val rawPublicKey = Ece.publicKeyToRaw(keyPair.public as ECPublicKey)
        val publicKey: String = BASE64_URL_ENCODER.encodeToString(rawPublicKey)
        val cacheKey: String = BASE64_URL_ENCODER.encodeToString(rawPublicKey.sha256()).take(22)
    }

    private class FreshPages(val chapterUrl: String, val urls: Map<String, HttpUrl>, val until: Long)

    companion object {
        private val BASE64_URL = Base64.getUrlDecoder()
        private val BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding()
        private val WRAP_INFO = "duck-reader-wrap-v1".toByteArray()
        private val PAYLOAD_MAGIC = "DRP1".toByteArray()
        private val JPEG_MEDIA_TYPE = "image/jpeg".toMediaType()
        private const val FRESH_MILLIS = 4 * 60 * 1000L
    }
}
