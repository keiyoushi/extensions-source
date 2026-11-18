package eu.kanade.tachiyomi.extension.es.aedex

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import eu.kanade.tachiyomi.multisrc.catharsis.TilePlan
import eu.kanade.tachiyomi.multisrc.catharsis.descramble
import keiyoushi.utils.hmacSha256
import keiyoushi.utils.parseAs
import keiyoushi.utils.sha256
import keiyoushi.utils.toJsonRequestBody
import keiyoushi.utils.xor
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt
import kotlin.time.Clock

// Free-mode pages: XOR-masked, and shuffled over an 8x8 grid when the page has a scramble key
class ImageInterceptor : Interceptor {

    private val liveTokens = ConcurrentHashMap<String, LiveToken>()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val fragment = request.url.fragment ?: return chain.proceed(request)
        val parts = fragment.split(':')
        if (parts.size != 5) return chain.proceed(request)

        val t = parts[0]
        val seed = parts[1]
        val h = parts[2]
        val clockOffset = parts[3].toLongOrNull() ?: 0L
        val xorByte = parts[4].toIntOrNull() ?: LEGACY_XOR_KEY

        val key = "$t:$seed".sha256().toHexString()
        val pk = "$key:p".sha256()

        val proofHeader = "bv-sig-${key.substring(7, 15)}"
        val liveHeader = "bv-live-${key.substring(15, 23)}"
        val buildHeader = "bv-build-${key.substring(23, 31)}"
        val buildId = key.substring(31, 43)
        val vivo = key.substring(43, 51)

        fun proofFor(kind: String, payload: String): String {
            val now = Clock.System.now().toEpochMilliseconds() + clockOffset
            val sig = "$kind.$payload.$now".hmacSha256(pk).toHexString().take(40)
            return "$now.$sig"
        }

        val readerOrigin = "${request.url.scheme}://${request.url.host}"
        val referer = request.header("Referer") ?: "$readerOrigin/"

        fun fetchLiveToken(): String? {
            val cached = liveTokens[h]
            val nowMs = Clock.System.now().toEpochMilliseconds()
            if (cached != null && nowMs < cached.expiresAt) {
                return cached.token
            }
            synchronized(liveTokens) {
                val recheck = liveTokens[h]
                if (recheck != null && Clock.System.now().toEpochMilliseconds() < recheck.expiresAt) {
                    return recheck.token
                }
                val liveUrl = request.url.newBuilder()
                    .encodedPath("/lector/$vivo/")
                    .query(null)
                    .fragment(null)
                    .build()
                val liveReq = request.newBuilder()
                    .url(liveUrl)
                    .post(LiveTokenRequest(h).toJsonRequestBody())
                    .header(proofHeader, proofFor("v", h))
                    .header(buildHeader, buildId)
                    .apply {
                        if (request.header("Origin") == null) {
                            header("Origin", readerOrigin)
                        }
                        if (request.header("Referer") == null) {
                            header("Referer", referer)
                        }
                    }
                    .build()
                return chain.proceed(liveReq).use { res ->
                    if (res.isSuccessful) {
                        val data = res.parseAs<LiveTokenDto>()
                        val ttl = data.ttl ?: 45000L
                        val expiry = Clock.System.now().toEpochMilliseconds() + maxOf(5000L, ttl - 5000L)
                        liveTokens[h] = LiveToken(data.token, expiry)
                        data.token
                    } else {
                        null
                    }
                }
            }
        }

        val handle = request.url.pathSegments.last()
        fun buildPageRequest(liveToken: String?): Request = request.newBuilder()
            .url(request.url.newBuilder().fragment(null).build())
            .header(buildHeader, buildId)
            .header(proofHeader, proofFor("p", handle))
            .apply {
                if (!liveToken.isNullOrBlank()) {
                    header(liveHeader, liveToken)
                }
                if (request.header("Origin") == null) {
                    header("Origin", readerOrigin)
                }
                if (request.header("Referer") == null) {
                    header("Referer", referer)
                }
            }
            .build()

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
        val result = descramble(image) { width, height -> tilePlan(mulberry32(scrambleKey.hashCode()), width, height) }
        image.recycle()

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
    }
}

private class LiveToken(val token: String, val expiresAt: Long)

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
