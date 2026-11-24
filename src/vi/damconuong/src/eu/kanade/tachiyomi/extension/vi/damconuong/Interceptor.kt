package eu.kanade.tachiyomi.extension.vi.damconuong

import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import app.cash.quickjs.QuickJs
import keiyoushi.lib.imagedecoder.ImageDecoder
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.utils.aesDecrypt
import keiyoushi.utils.aesGcm
import keiyoushi.utils.decodeHex
import keiyoushi.utils.hmacSha256
import keiyoushi.utils.parseAs
import keiyoushi.utils.readIntBigEndian
import keiyoushi.utils.writeIntBigEndian
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import org.jsoup.nodes.Document
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

// ============================== Decoder secret =============================

/** Key the page decoder feeds its HMAC chain, scraped from the decoder bundle. */
object DecoderSecret {
    private const val KEY_SIZE = 32

    private const val PREF_SECRET = "decoder_key_hex"

    private val NULL_IMPORT_RE = Regex("""\bimport\s*\(""")

    /**
     * Stored key when still valid, otherwise a freshly scraped one. The bundle only
     * runs on a miss or when [knownStale] says the server rejected the stored key.
     */
    suspend fun load(
        client: OkHttpClient,
        baseUrl: String,
        chapterPage: Document,
        headers: Headers,
        preferences: SharedPreferences,
        knownStale: Boolean = false,
    ): ByteArray {
        val stored = preferences.getString(PREF_SECRET, null)?.decodeHex()?.takeIf { it.size == KEY_SIZE }
        if (stored != null && !knownStale) return stored

        val fetched = runBundle(client, baseUrl, chapterPage, headers) ?: return checkNotNull(stored) {
            "Could not read the page decoder key"
        }
        preferences.edit().putString(PREF_SECRET, fetched.toHexString()).apply()
        return fetched
    }

    private suspend fun runBundle(client: OkHttpClient, baseUrl: String, chapterPage: Document, headers: Headers): ByteArray? {
        val decoder = chapterPage.selectFirst("[data-cipher-decoder]")?.attr("data-cipher-decoder")
            ?: return null
        val url = chapterPage.absUrl("abs:data-cipher-decoder").ifEmpty { resolve(baseUrl, decoder) }

        val script = try {
            client.get(url, headers, Fetch.SCRIPT).use { it.body.string() }
                .replace("export default", "globalThis.__decoder = ")
                .replace(NULL_IMPORT_RE, "globalThis.__noImport(")
        } catch (_: Exception) {
            return null
        }

        val host = baseUrl.toHttpUrlOrNull()?.host ?: return null
        return captureKey(script, host)?.takeIf { it.size == KEY_SIZE }
    }

    private fun resolve(baseUrl: String, path: String): String = if (path.startsWith("http")) path else baseUrl.trimEnd('/') + "/" + path.trimStart('/')

    private fun captureKey(script: String, host: String): ByteArray? = try {
        QuickJs.create().use { js ->
            js.evaluate(shims(host))
            js.evaluate(script)
            val key = (js.evaluate(CAPTURE_SCRIPT) as? Array<*>)?.filterIsInstance<Number>()
            key?.let { values -> ByteArray(values.size) { values[it].toInt().toByte() } }
        }
    } catch (_: Exception) {
        null
    }

    // A page origin, a UTF-8 encoder and the WebCrypto calls the bundle makes
    private fun shims(host: String): String = """
        globalThis.location = { hostname: "$host", protocol: "https:", href: "https://$host/" };
        globalThis.self = globalThis;
        globalThis.window = globalThis;
        globalThis.__secret = null;
        globalThis.__noImport = function () { return Promise.reject(new Error("offline")); };
        globalThis.document = { createElement: function () { return { getContext: function () { return {} } }; } };
        globalThis.TextEncoder = function () {};
        globalThis.TextEncoder.prototype.encode = function (text) {
            var out = [];
            for (var i = 0; i < text.length; i++) {
                var c = text.charCodeAt(i);
                if (c < 0x80) out.push(c);
                else if (c < 0x800) out.push(0xc0 | (c >> 6), 0x80 | (c & 63));
                else out.push(0xe0 | (c >> 12), 0x80 | ((c >> 6) & 63), 0x80 | (c & 63));
            }
            return new Uint8Array(out);
        };
        crypto = { subtle: {
            importKey: function (format, key) {
                if (key && key.length === 32 && globalThis.__secret === null) globalThis.__secret = key;
                return Promise.resolve({ export: function () { return key; } });
            },
            sign: function () { return Promise.resolve(new ArrayBuffer(32)); },
            decrypt: function () { return Promise.resolve(new ArrayBuffer(0)); },
            digest: function () { return Promise.resolve(new ArrayBuffer(32)); },
        } };
    """.trimIndent()

    private const val CAPTURE_SCRIPT = """
        (function () {
            var key = globalThis.__secret, out = [];
            for (var i = 0; key && i < key.length; i++) out.push(key[i] & 255);
            return out;
        })()
    """
}

// ============================== Pages crypto ===============================

object PagesCrypto {
    const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    private var loadedSecret: ByteArray? = null

    private var tokKey: ByteArray? = null

    private var encKey: ByteArray? = null

    fun load(secret: ByteArray) {
        if (loadedSecret.contentEquals(secret) && tokKey != null) return
        loadedSecret = secret
        tokKey = "tok".hmacSha256(secret)
        encKey = "enc".hmacSha256(secret)
    }

    private val hmacKeys: Pair<ByteArray, ByteArray>
        get() = checkNotNull(tokKey?.let { it to checkNotNull(encKey) }) { "PagesCrypto not loaded" }

    fun token(mangaSlug: String, chapterSlug: String): String {
        val mac = "$mangaSlug/$chapterSlug".hmacSha256(hmacKeys.first).copyOf(16)
        val bytes = ByteArray(17)
        bytes[0] = 1
        mac.copyInto(bytes, 1)
        return encodeBase64Url(bytes)
    }

    fun decryptPages(encrypted: String, token: String, path: String): PagesPayload {
        val raw = decodeBase64Url(encrypted) ?: error("bad payload")
        if (raw.size < 12 + 16) error("bad payload")
        val iv = raw.copyOfRange(0, 12)
        val cipherBytes = raw.copyOfRange(12, raw.size)

        val aesKey = token.hmacSha256(hmacKeys.second)
        val cipher = aesGcm(aesKey, iv).apply { updateAAD(path.toByteArray(StandardCharsets.UTF_8)) }
        return String(cipherBytes.aesDecrypt(cipher), StandardCharsets.UTF_8).parseAs()
    }

    private fun encodeBase64Url(input: ByteArray): String = buildString {
        var i = 0
        while (i < input.size) {
            val b0 = input[i].toInt() and 0xff
            val b1 = if (i + 1 < input.size) input[i + 1].toInt() and 0xff else 0
            val b2 = if (i + 2 < input.size) input[i + 2].toInt() and 0xff else 0
            val n = (b0 shl 16) or (b1 shl 8) or b2
            val chars = minOf(4, (input.size - i) * 8 / 6 + 1)
            for (c in 0 until chars) append(ALPHABET[(n shr (18 - 6 * c)) and 63])
            i += 3
        }
    }

    internal fun decodeBase64Url(input: String): ByteArray? {
        val out = ByteArrayOutputStream(input.length)
        var bits = 0
        var value = 0
        for (ch in input) {
            val d = ALPHABET.indexOf(ch)
            if (d < 0) return null
            value = (value shl 6) or d
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out.write((value shr bits) and 0xff)
            }
        }
        return out.toByteArray()
    }
}

// ================================= Scramble ================================

object Scramble {
    private const val PREFIX = "x1."
    private const val KEY_LENGTH = 46
    private const val SEED_LENGTH = 32

    fun shouldDescramble(urlFragment: String?): String? {
        if (urlFragment.isNullOrEmpty()) return null
        if (!urlFragment.startsWith(PREFIX) || urlFragment.length != KEY_LENGTH) return null
        return urlFragment
    }

    fun descramble(bitmap: Bitmap, key: String): Bitmap {
        val seed = decodeBase64Url(key.removePrefix(PREFIX)) ?: return bitmap
        if (seed.size != SEED_LENGTH) return bitmap

        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return bitmap

        val layout = buildLayout(seed, height) ?: return bitmap
        val out = Bitmap.createBitmap(width, height, bitmap.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        for ((dstY, srcY, tileH) in layout) {
            canvas.drawBitmap(
                bitmap,
                Rect(0, srcY, width, srcY + tileH),
                Rect(0, dstY, width, dstY + tileH),
                null,
            )
        }
        return out
    }

    private fun buildLayout(seed: ByteArray, height: Int): List<Triple<Int, Int, Int>>? {
        val rng = HmacPrng(seed)
        val rows = 8 + rng.nextInt(13)
        val tileH = 16 * (height / (16 * rows))
        if (tileH < 16) return null

        val perm = IntArray(rows) { it }
        for (i in rows - 1 downTo 1) {
            val j = rng.nextInt(i + 1)
            val tmp = perm[i]
            perm[i] = perm[j]
            perm[j] = tmp
        }

        val total = rows * tileH
        if (total > height) return null

        val layout = ArrayList<Triple<Int, Int, Int>>(rows + 1)
        for (i in 0 until rows) {
            layout.add(Triple(perm[i] * tileH, i * tileH, tileH))
        }
        if (height > total) {
            layout.add(Triple(total, total, height - total))
        }
        return layout
    }

    private fun decodeBase64Url(input: String): ByteArray? = PagesCrypto.decodeBase64Url(input)
}

private class HmacPrng(seed: ByteArray) {
    private val mac = Mac.getInstance("HmacSHA256").apply {
        init(SecretKeySpec(seed, "HmacSHA256"))
    }
    private var counter = 0
    private var buf = ByteArray(0)
    private var pos = 0

    private fun nextUint32(): Int {
        if (pos >= buf.size) {
            val msg = ByteArray(4)
            msg.writeIntBigEndian(0, counter++)
            buf = mac.doFinal(msg)
            pos = 0
        }
        val v = buf.readIntBigEndian(pos)
        pos += 4
        return v
    }

    fun nextInt(n: Int): Int {
        val limit = 0x100000000L / n * n
        var r = nextUint32().toLong() and 0xffffffffL
        while (r >= limit) {
            r = nextUint32().toLong() and 0xffffffffL
        }
        return (r % n).toInt()
    }
}

// ============================ Image interceptor ============================

class ScrambleInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val key = Scramble.shouldDescramble(request.url.fragment) ?: return chain.proceed(request)

        val cleanUrl = request.url.newBuilder().fragment(null).build()
        val response = chain.proceed(request.newBuilder().url(cleanUrl).build())
        if (!response.isSuccessful) return response

        val decoded = response.body.byteStream().use { ImageDecoder.decode(it.readBytes()) }
        val out = try {
            Scramble.descramble(decoded, key)
        } finally {
            decoded.recycle()
        }

        val buffer = Buffer()
        out.compress(Bitmap.CompressFormat.PNG, 100, buffer.outputStream())
        out.recycle()
        return response.newBuilder()
            .body(buffer.asResponseBody("image/png".toMediaType()))
            .build()
    }
}
