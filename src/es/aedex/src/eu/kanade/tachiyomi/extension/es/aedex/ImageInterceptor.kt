package eu.kanade.tachiyomi.extension.es.aedex

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import eu.kanade.tachiyomi.multisrc.catharsis.TilePlan
import eu.kanade.tachiyomi.multisrc.catharsis.descramble
import keiyoushi.network.Fetch
import keiyoushi.network.fetch
import keiyoushi.utils.parseAs
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

// Pages of the free reader: signed requests, XOR-masked images, shuffled over an 8x8 grid when the page has a key
class ImageInterceptor(
    // A new visit to the reader for a chapter, given the seed of the stale one
    private val renewSession: (chapterKey: String, staleSeed: String) -> ReaderSession?,
) : Interceptor {

    private val liveTokens = ConcurrentHashMap<String, LiveToken>()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val fragment = request.url.fragment
        if (fragment == null || request.url.pathSegments.take(2) != listOf("lector", "p")) return chain.proceed(request)
        val parts = fragment.split(':', limit = 6)
        val keys = VisitKeys.parse(parts)
        val index = parts[4].toInt()
        val chapterKey = parts[5]

        val response = try {
            proceedPage(chain, request, keys)
        } catch (_: OutdatedVisitException) {
            // The site forgot the visit this URL came from, so take the page from a new one
            val session = renewSession(chapterKey, keys.seed)?.takeIf { index < it.pageCount } ?: throw OutdatedVisitException()
            val renewed = request.newBuilder()
                .url(session.imageUrl(index, chapterKey))
                .fetch(Fetch.XHR.from(session.readerUrl))
                .build()
            proceedPage(chain, renewed, session.keys)
        }
        if (!response.isSuccessful) return response

        val pageXorKey = response.header("X-Page-Xor")?.toIntOrNull()
        val scrambleKey = response.header("X-Page-Key").orEmpty()
        // The reader spaces the shuffled strips apart by this many px
        val tileGutter = response.header("X-Tile-Gutter")?.toIntOrNull() ?: 0

        // Plain, the page's XOR key, then 0x43 for older chapters, like the site's reader
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
        val result = descramble(image, tileGutter) { width, height -> tilePlan(mulberry32(scrambleKey.hashCode()), width, height) }
        image.recycle()

        val buffer = Buffer()
        result.compress(Bitmap.CompressFormat.JPEG, 90, buffer.outputStream())
        result.recycle()
        return response.newBuilder()
            .body(buffer.asResponseBody(MEDIA_TYPE, buffer.size))
            .build()
    }

    private fun proceedPage(chain: Interceptor.Chain, request: Request, keys: VisitKeys): Response {
        val signer = keys.signer
        val (proofName, proof) = signer.proof("p", request.url.pathSegments[2], keys.now())
        val response = chain.proceed(
            request.newBuilder()
                .url(request.url.newBuilder().fragment(null).build())
                .header(signer.buildName, signer.buildId)
                .header(proofName, proof)
                .header(signer.liveName, liveToken(chain, request, keys))
                .build(),
        )
        if (response.code == 409) {
            response.close()
            throw OutdatedVisitException()
        }
        return response
    }

    // The site's heartbeat: pages are only served along a live token, which it hands out for a while
    private fun liveToken(chain: Interceptor.Chain, request: Request, keys: VisitKeys): String {
        fun cached() = liveTokens[keys.liveKey]?.takeIf { it.expiresAt > Clock.System.now().toEpochMilliseconds() }?.token
        cached()?.let { return it }
        synchronized(liveTokens) {
            cached()?.let { return it }
            val signer = keys.signer
            val (proofName, proof) = signer.proof("v", keys.liveKey, keys.now())
            val live = chain.proceed(
                request.newBuilder()
                    .url(request.url.newBuilder().encodedPath("/lector/${signer.livePath}/").query(null).fragment(null).build())
                    .post(LiveRequest(keys.liveKey).toJsonRequestBody())
                    .header(signer.buildName, signer.buildId)
                    .header(proofName, proof)
                    .build(),
            ).use { response ->
                if (response.code == 409) throw OutdatedVisitException()
                if (!response.isSuccessful) throw IOException("HTTP error ${response.code}")
                response.parseAs<LiveTokenDto>()
            }
            // Renewed a little before it runs out
            val expiresAt = Clock.System.now().toEpochMilliseconds() + maxOf(MIN_LIVE_TTL, live.ttl - LIVE_MARGIN)
            liveTokens[keys.liveKey] = LiveToken(live.token, expiresAt)
            return live.token
        }
    }

    private fun VisitKeys.now() = Clock.System.now().toEpochMilliseconds() + clockOffset

    private class LiveToken(val token: String, val expiresAt: Long)

    private class OutdatedVisitException : IOException("The site's reader changed, open the chapter again")

    companion object {
        private const val LEGACY_XOR_KEY = 0x43
        private const val HEADER_SIZE = 12L
        private const val MIN_LIVE_TTL = 5_000L
        private const val LIVE_MARGIN = 5_000L
        private val MEDIA_TYPE = "image/jpeg".toMediaType()
    }
}

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
