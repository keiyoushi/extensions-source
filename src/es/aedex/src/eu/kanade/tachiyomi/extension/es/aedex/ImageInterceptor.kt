package eu.kanade.tachiyomi.extension.es.aedex

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import eu.kanade.tachiyomi.multisrc.catharsis.TilePlan
import eu.kanade.tachiyomi.multisrc.catharsis.descramble
import keiyoushi.utils.parseAs
import keiyoushi.utils.xor
import kotlinx.serialization.Serializable
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import java.io.IOException
import java.net.URLDecoder
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.roundToInt

// Free-mode pages: XOR-masked, and shuffled over an 8x8 grid when the page has a scramble key
class ImageInterceptor(private val client: OkHttpClient) : Interceptor {

    private val liveTokens = ConcurrentHashMap<String, LiveToken>()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val fragment = request.url.fragment ?: return chain.proceed(request)

        if (fragment.startsWith("proto&")) {
            return interceptProto(chain, request, fragment)
        }

        if (request.url.pathSegments.getOrNull(3) != "free-pages") return chain.proceed(request)
        val pageXorKey = fragment.substringBefore(':').toIntOrNull()
        val scrambleKey = fragment.substringAfter(':')
        val response = chain.proceed(request)
        if (!response.isSuccessful) return response

        return processImage(response, pageXorKey, scrambleKey)
    }

    private fun interceptProto(chain: Interceptor.Chain, request: Request, fragment: String): Response {
        val params = fragment.removePrefix("proto&").split('&').associate {
            val idx = it.indexOf('=')
            if (idx != -1) it.substring(0, idx) to it.substring(idx + 1) else it to ""
        }
        val t = params["t"] ?: return chain.proceed(request)
        val seed = params["seed"] ?: return chain.proceed(request)
        val h = params["h"] ?: return chain.proceed(request)
        val clockOffset = params["offset"]?.toLongOrNull() ?: 0L
        val xorByte = params["xor"]?.toIntOrNull() ?: 0x43
        val ref = URLDecoder.decode(params["ref"].orEmpty(), "UTF-8")
        val readerOrigin = "${request.url.scheme}://${request.url.host}"
        val handle = request.url.pathSegments.let { segments ->
            val pIdx = segments.indexOf("p")
            if (pIdx != -1 && pIdx + 1 < segments.size) segments[pIdx + 1] else ""
        }

        val key = sha256Hex("$t:$seed")
        val pk = sha256Bytes("$key:p")
        val proofHeader = "bv-sig-${key.substring(7, 15)}"
        val liveHeader = "bv-live-${key.substring(15, 23)}"
        val buildHeader = "bv-build-${key.substring(23, 31)}"
        val buildId = key.substring(31, 43)
        val vivo = key.substring(43, 51)

        fun proofFor(scope: String, subject: String): String {
            val time = System.currentTimeMillis() + clockOffset
            val msg = "$scope.$subject.$time".toByteArray(Charsets.UTF_8)
            val sig = hmacSha256Hex(pk, msg).take(40)
            return "$time.$sig"
        }

        fun fetchLiveToken(): String? {
            val cached = liveTokens[h]
            if (cached != null && System.currentTimeMillis() < cached.expiresAt) {
                return cached.token
            }
            synchronized(liveTokens) {
                val recheck = liveTokens[h]
                if (recheck != null && System.currentTimeMillis() < recheck.expiresAt) {
                    return recheck.token
                }
                return try {
                    val proof = proofFor("v", h)
                    val liveReq = Request.Builder()
                        .url("$readerOrigin/lector/$vivo/")
                        .post("""{"h":"$h"}""".toRequestBody(JSON_MEDIA_TYPE))
                        .header("Content-Type", "application/json")
                        .header(proofHeader, proof)
                        .header(buildHeader, buildId)
                        .header("Origin", readerOrigin)
                        .header("Referer", ref)
                        .build()
                    client.newCall(liveReq).execute().use { res ->
                        if (res.isSuccessful) {
                            val data = res.body.string().parseAs<LiveTokenDto>()
                            val token = data.l
                            val ttl = data.ttl ?: 45000L
                            val expiry = System.currentTimeMillis() + maxOf(5000L, ttl - 5000L)
                            liveTokens[h] = LiveToken(token, expiry)
                            token
                        } else {
                            null
                        }
                    }
                } catch (_: Exception) {
                    null
                }
            }
        }

        fun buildPageRequest(liveToken: String?): Request {
            val pageProof = proofFor("p", handle)
            return request.newBuilder()
                .url(request.url.newBuilder().fragment(null).build())
                .header(buildHeader, buildId)
                .header(proofHeader, pageProof)
                .apply {
                    if (!liveToken.isNullOrBlank()) {
                        header(liveHeader, liveToken)
                    }
                }
                .header("Origin", readerOrigin)
                .header("Referer", ref)
                .build()
        }

        var liveToken = fetchLiveToken()
        var response = chain.proceed(buildPageRequest(liveToken))
        if (response.code == 403) {
            liveTokens.remove(h)
            response.close()
            liveToken = fetchLiveToken()
            response = chain.proceed(buildPageRequest(liveToken))
        }
        if (!response.isSuccessful) return response

        val scrambleKey = response.header("X-Page-Key").orEmpty()
        val pageXorKey = response.header("X-Page-Xor")?.toIntOrNull() ?: xorByte
        return processImage(response, pageXorKey, scrambleKey)
    }

    private fun processImage(response: Response, pageXorKey: Int?, scrambleKey: String): Response {
        val header = response.body.source().peek().readByteArray(HEADER_SIZE)
        val xorKey = listOfNotNull(0, pageXorKey, LEGACY_XOR_KEY).firstOrNull { isImageHeader(header.xor(it)) }
        if (xorKey == null) {
            response.close()
            throw IOException("Unknown page image format")
        }
        val plain = if (xorKey == 0) response else response.xor(xorKey)
        if (scrambleKey.isBlank()) return plain
        val image = plain.body.use { BitmapFactory.decodeStream(it.byteStream()) }
            ?: throw IOException("Undecodable page image")
        val result = if (image.width >= 200 && image.height >= 200) {
            descramble(image) { width, height -> tilePlan(mulberry32(scrambleKey.hashCode()), width, height) }
        } else {
            image
        }
        if (result !== image) {
            image.recycle()
        }

        val buffer = Buffer()
        result.compress(Bitmap.CompressFormat.JPEG, 90, buffer.outputStream())
        result.recycle()
        return response.newBuilder()
            .body(buffer.asResponseBody(MEDIA_TYPE, buffer.size))
            .build()
    }

    companion object {
        private const val LEGACY_XOR_KEY = 0x43
        private const val HEADER_SIZE = 12L
        private val MEDIA_TYPE = "image/jpeg".toMediaType()
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()

        private fun sha256Hex(text: String): String = MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        private fun sha256Bytes(text: String): ByteArray = MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))

        private fun hmacSha256Hex(key: ByteArray, data: ByteArray): String {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(key, "HmacSHA256"))
            return mac.doFinal(data).joinToString("") { "%02x".format(it) }
        }
    }
}

private class LiveToken(val token: String, val expiresAt: Long)

@Serializable
class LiveTokenDto(
    val l: String,
    val ttl: Long? = null,
)

// The reader's page shuffle generator
private fun mulberry32(seed: Int): () -> Double {
    var state = seed
    return {
        state += 0x6d2b79f5
        var t = state
        t = (t xor (t ushr 15)) * (t or 1)
        t = t xor (t + (t xor (t ushr 7)) * (t or 61))
        (t xor (t ushr 14)).toUInt().toDouble() / 4294967296.0
    }
}

// The reader's 8x8 plan drawn from [random]
private fun tilePlan(random: () -> Double, width: Int, height: Int): TilePlan {
    val cols = boundaries(random, width)
    val rows = boundaries(random, height)
    return TilePlan(cols, rows, permutation(random), permutation(random))
}

private fun boundaries(random: () -> Double, total: Int): IntArray {
    val bounds = IntArray(GRID + 1)
    bounds[GRID] = total
    val base = total / GRID
    var position = 0
    for (i in 0 until GRID - 1) {
        val max = total - position - (GRID - 1 - i) * MIN_TILE
        position += (base + ((random() * 0.7 - 0.35) * base).roundToInt()).coerceIn(MIN_TILE, maxOf(MIN_TILE, max))
        bounds[i + 1] = position
    }
    return bounds
}

private fun permutation(random: () -> Double): IntArray {
    val order = IntArray(GRID) { it }
    for (i in GRID - 1 downTo 1) {
        val j = (random() * (i + 1)).toInt()
        order[i] = order[j].also { order[j] = order[i] }
    }
    return order
}

private const val GRID = 8
private const val MIN_TILE = 16
