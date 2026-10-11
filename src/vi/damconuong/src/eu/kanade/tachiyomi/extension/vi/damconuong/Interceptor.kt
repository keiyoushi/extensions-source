package eu.kanade.tachiyomi.extension.vi.damconuong

import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import keiyoushi.utils.parseAs
import keiyoushi.utils.rc4
import keiyoushi.utils.readIntBigEndian
import keiyoushi.utils.writeIntBigEndian
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

private fun OkHttpClient.getString(url: String): String {
    val response = newCall(Request.Builder().url(url).build()).execute()
    return response.use { it.body.string() }
}

// =============================== Site cache ================================

object SiteCache {
    private const val KEY_SECRET = "decoder_secret"
    private const val KEY_ALPHABET = "decoder_alphabet"

    fun decoderSecret(prefs: SharedPreferences): String? = prefs.getString(KEY_SECRET, null)

    fun decoderAlphabet(prefs: SharedPreferences): String? = prefs.getString(KEY_ALPHABET, null)

    fun saveDecoder(prefs: SharedPreferences, secret: String, alphabet: String) {
        prefs.edit()
            .putString(KEY_SECRET, secret)
            .putString(KEY_ALPHABET, alphabet)
            .apply()
    }

    fun invalidate(prefs: SharedPreferences) {
        prefs.edit()
            .remove(KEY_SECRET)
            .remove(KEY_ALPHABET)
            .apply()
    }
}

// =========================== Decoder string scrape =========================

object DecoderScraper {
    data class Config(
        val secret: String,
        val tokenAlphabet: String,
    )

    suspend fun scrape(client: OkHttpClient, baseUrl: String, prefs: SharedPreferences? = null): Config {
        prefs?.let { p ->
            val cachedSecret = SiteCache.decoderSecret(p)
            val cachedAlphabet = SiteCache.decoderAlphabet(p)
            if (cachedSecret != null && cachedAlphabet != null) {
                return Config(cachedSecret, cachedAlphabet)
            }
        }

        val config = try {
            scrapeFromSite(client, baseUrl)
        } catch (e: IOException) {
            prefs?.let { SiteCache.invalidate(it) }
            scrapeFromSite(client, baseUrl)
        }
        prefs?.let { SiteCache.saveDecoder(it, config.secret, config.tokenAlphabet) }
        return config
    }

    private suspend fun scrapeFromSite(client: OkHttpClient, baseUrl: String): Config {
        val js = fetchDecoderJs(client, baseUrl)
        val obfAlphabet = OBF_B64_RE.find(js)?.groupValues?.get(1)
            ?: error("obfuscator alphabet not found")
        val strings = decodeStringTable(js, obfAlphabet)
        val secret = strings.values.firstOrNull { it.matches(SECRET_RE) }
            ?: error("decoder secret not found")
        val tokenAlphabet = strings.values.firstOrNull { it.length == 64 && ALPHABET_RE.matches(it) }
            ?: error("token alphabet not found")
        return Config(secret, tokenAlphabet)
    }

    private suspend fun fetchDecoderJs(client: OkHttpClient, baseUrl: String): String {
        val bookmarkUrl = "$baseUrl/js/bookmark.js"
        val bookmarkBody = runCatching { client.getString(bookmarkUrl) }.getOrDefault("")
        if (isDecoderBundle(bookmarkBody)) return bookmarkBody

        val home = client.getString(baseUrl)
        val decoderAttr = CIPHER_DECODER_RE.find(home)?.groupValues?.get(1)
        if (decoderAttr != null) {
            val url = if (decoderAttr.startsWith("http")) decoderAttr else "$baseUrl/${decoderAttr.trimStart('/')}"
            val body = runCatching { client.getString(url) }.getOrDefault("")
            if (isDecoderBundle(body)) return body
        }

        val pending = ArrayDeque<String>()
        val seen = HashSet<String>()
        fun add(ref: String) {
            if (seen.add(ref)) pending.add(ref)
        }
        CHUNK_RE.findAll(home).forEach { add(it.groupValues[1]) }
        NESTED_CHUNK_RE.findAll(home).forEach { add(it.groupValues[1]) }
        SCRIPT_SRC_RE.findAll(home).forEach { add(it.groupValues[1]) }

        fun resolveUrl(ref: String): String {
            val root = baseUrl.trimEnd('/')
            return when {
                ref.startsWith("http") -> ref
                ref.startsWith("/_next/") -> root + ref
                ref.startsWith("/") -> root + ref
                ref.startsWith("static/") -> "$root/_next/$ref"
                else -> "$root/_next/static/chunks/$ref"
            }
        }

        while (pending.isNotEmpty()) {
            val ref = pending.removeFirst()
            val body = runCatching { client.getString(resolveUrl(ref)) }.getOrDefault("")
            if (isDecoderBundle(body)) return body
            NESTED_CHUNK_RE.findAll(body).forEach { add(it.groupValues[1]) }
        }
        error("decoder bundle not found")
    }

    private fun isDecoderBundle(body: String): Boolean {
        if (body.isEmpty()) return false
        return STRING_ARRAY_RE.containsMatchIn(body) &&
            body.contains("decodeURIComponent") &&
            (body.contains("for(;;)") || body.contains("while(!![])")) &&
            body.contains("parseInt")
    }

    private fun decodeStringTable(js: String, obfAlphabet: String): Map<String, String> {
        val arrayMatch = STRING_ARRAY_RE.find(js) ?: error("decoder string table not found")
        val tableContent = arrayMatch.groupValues[1].ifEmpty { arrayMatch.groupValues[2] }
        val rawStrings = parseJsStringArray(tableContent)
        val pairs = PAIR_RE.findAll(js)
            .mapNotNull { m ->
                val idxStr = m.groupValues[1].ifEmpty { m.groupValues[3] }
                val key = m.groupValues[2].ifEmpty { m.groupValues[4] }
                val idx = if (idxStr.startsWith("0x", ignoreCase = true)) {
                    idxStr.substring(2).toInt(16)
                } else {
                    idxStr.toIntOrNull() ?: return@mapNotNull null
                }
                idx to key
            }
            .distinct()
            .toList()
        val indexOffset = INDEX_OFFSET_RE.find(js)?.let { match ->
            val raw = match.groupValues.drop(1).firstOrNull { it.isNotEmpty() }
            raw?.let {
                if (it.startsWith("0x", ignoreCase = true)) {
                    it.substring(2).toInt(16)
                } else {
                    it.toInt()
                }
            }
        } ?: 187

        val table = ArrayList(rawStrings)
        repeat(table.size) {
            val decoder = StringDecoder(table, obfAlphabet, indexOffset)
            val out = HashMap<String, String>()
            for ((index, key) in pairs) {
                runCatching { out["$index|$key"] = decoder.decode(index, key) }
            }
            val secret = out.values.firstOrNull { it.matches(SECRET_RE) }
            val tokenAlpha = out.values.firstOrNull { it.length == 64 && ALPHABET_RE.matches(it) }
            if (secret != null && tokenAlpha != null) {
                return out
            }
            table.add(table.removeAt(0))
        }
        error("decoder string table rotation failed")
    }

    private fun parseJsStringArray(body: String): List<String> {
        val out = ArrayList<String>()
        val re = Regex("['\"]((?:\\\\.|[^'\"\\\\])*)['\"]")
        for (m in re.findAll(body)) {
            out += m.groupValues[1]
                .replace("\\\\", "\\")
                .replace("\\\"", "\"")
                .replace("\\'", "'")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
        }
        return out
    }

    private class StringDecoder(
        private val table: List<String>,
        private val obfAlphabet: String,
        private val indexOffset: Int,
    ) {
        private val cache = HashMap<Int, String>()

        fun decode(index: Int, key: String): String {
            val adjusted = index - indexOffset
            if (adjusted !in table.indices) error("bad index")
            cache[adjusted]?.let { return it }
            val plain = rc4(customB64Decode(table[adjusted]), key)
            cache[adjusted] = plain
            return plain
        }

        private fun customB64Decode(input: String): String {
            val bytes = ArrayList<Byte>()
            var t = 0
            var o = 0
            for (element in input) {
                val r = obfAlphabet.indexOf(element)
                if (r < 0) continue
                t = if (o % 4 != 0) 64 * t + r else r
                val old = o
                o += 1
                if (old % 4 != 0) {
                    bytes.add(((t shr ((-2 * o) and 6)) and 0xff).toByte())
                }
            }
            return String(bytes.toByteArray(), StandardCharsets.UTF_8)
        }

        private fun rc4(input: String, key: String): String {
            val data = ByteArray(input.length) { input[it].code.toByte() }
            val out = data.rc4(key.toByteArray(StandardCharsets.ISO_8859_1))
            return out.toString(StandardCharsets.ISO_8859_1)
        }
    }

    private val CIPHER_DECODER_RE = Regex("data-cipher-decoder=\"([^\"]+)\"")
    private val SCRIPT_SRC_RE = Regex("<script[^>]+src=\"([^\"]+\\.js[^\"]*)\"")
    private val SECRET_RE = Regex("^[A-Za-z0-9_-]{43,44}$")
    private val ALPHABET_RE = Regex("^[A-Za-z0-9+/_-]{64}$")
    private val OBF_B64_RE = Regex("['\"]([A-Za-z0-9+/=_-]{64,65})['\"]")
    private val STRING_ARRAY_RE = Regex(
        "(?:function\\s+[a-zA-Z0-9_$]+\\(\\)\\s*\\{\\s*(?:const|var|let)\\s+[a-zA-Z0-9_$]+=\\s*(\\[[^\\]]+\\])|function \\w+\\(\\)\\{let W=(\\[.*?\\]);return)",
        RegexOption.DOT_MATCHES_ALL,
    )
    private val PAIR_RE = Regex("(?:_0x[a-f0-9]+|\\b[a-zA-Z0-9_$]+)\\s*\\(\\s*(0x[0-9a-fA-F]+|\n\\d+)\\s*,\\s*['\"]([^'\"]+)['\"]\\s*\\)|\\w+\\((\\d+),\\s*\"([^\"]*)\"\\)")
    private val INDEX_OFFSET_RE = Regex("(?:_0x[a-f0-9]+|[a-zA-Z0-9_$]+)\\s*=\\s*(?:_0x[a-f0-9]+|[a-zA-Z0-9_$]+)\\s*-\\s*(0x[0-9a-fA-F]+|\\d+)|function \\w+\\(\\w+,\\w+\\)\\{\\w+-=(\\d+)")
    internal val CHUNK_RE = Regex("(?:src|href)=\"(/_next/static/chunks/[^\"]+\\.js)")
    private val NESTED_CHUNK_RE = Regex("static/chunks/([A-Za-z0-9_\\-\\.]+\\.js)")
}

// ============================== Pages crypto ===============================

object PagesCrypto {
    @Volatile private var tokKey: ByteArray? = null

    @Volatile private var encKey: ByteArray? = null

    @Volatile private var alphabet: String? = null

    fun tokenAlphabet(): String = checkNotNull(alphabet) { "PagesCrypto not loaded" }

    suspend fun ensureLoaded(client: OkHttpClient, baseUrl: String, prefs: SharedPreferences? = null) {
        if (tokKey != null) return
        val config = DecoderScraper.scrape(client, baseUrl, prefs)
        val secretBytes = decodeBase64Url(config.secret, config.tokenAlphabet)
            ?: error("bad decoder secret")
        alphabet = config.tokenAlphabet
        tokKey = hmac(secretBytes, "tok".toByteArray(StandardCharsets.UTF_8))
        encKey = hmac(secretBytes, "enc".toByteArray(StandardCharsets.UTF_8))
    }

    fun token(mangaSlug: String, chapterSlug: String): String {
        val tok = checkNotNull(tokKey) { "PagesCrypto not loaded" }
        val path = "$mangaSlug/$chapterSlug"
        val mac = hmac(tok, path.toByteArray(StandardCharsets.UTF_8)).copyOf(16)
        val bytes = ByteArray(17)
        bytes[0] = 1
        mac.copyInto(bytes, 1)
        return encodeBase64Url(bytes)
    }

    fun decryptPages(encrypted: String, token: String, path: String): PagesPayload {
        val enc = checkNotNull(encKey) { "PagesCrypto not loaded" }
        val raw = decodeBase64Url(encrypted, tokenAlphabet()) ?: error("bad payload")
        if (raw.size < 12 + 16) error("bad payload")
        val iv = raw.copyOfRange(0, 12)
        val cipherBytes = raw.copyOfRange(12, raw.size)

        val aesKey = hmac(enc, token.toByteArray(StandardCharsets.UTF_8))
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(aesKey, "AES"),
            GCMParameterSpec(128, iv),
        )
        cipher.updateAAD(path.toByteArray(StandardCharsets.UTF_8))
        val plain = cipher.doFinal(cipherBytes)
        return String(plain, StandardCharsets.UTF_8).parseAs()
    }

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    private fun encodeBase64Url(input: ByteArray): String {
        val b64 = tokenAlphabet()
        val out = StringBuilder()
        var i = 0
        while (i < input.size) {
            val b0 = input[i].toInt() and 0xff
            val b1 = if (i + 1 < input.size) input[i + 1].toInt() and 0xff else 0
            val b2 = if (i + 2 < input.size) input[i + 2].toInt() and 0xff else 0
            val n = (b0 shl 16) or (b1 shl 8) or b2
            val remain = input.size - i
            val chars = minOf(4, (remain * 8 + 5) / 6)
            for (c in 0 until chars) {
                out.append(b64[(n shr (18 - 6 * c)) and 63])
            }
            i += 3
        }
        return out.toString()
    }

    private fun decodeBase64Url(input: String, b64: String): ByteArray? {
        val out = ArrayList<Byte>()
        var bits = 0
        var value = 0
        for (ch in input) {
            val d = b64.indexOf(ch)
            if (d < 0) return null
            value = (value shl 6) or d
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out.add(((value shr bits) and 0xff).toByte())
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

    private fun decodeBase64Url(input: String): ByteArray? {
        val b64 = PagesCrypto.tokenAlphabet()
        val out = ArrayList<Byte>()
        var bits = 0
        var value = 0
        for (ch in input) {
            val d = b64.indexOf(ch)
            if (d < 0) return null
            value = (value shl 6) or d
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out.add(((value shr bits) and 0xff).toByte())
            }
        }
        return out.toByteArray()
    }
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
        val key = Scramble.shouldDescramble(request.url.fragment)
            ?: return chain.proceed(request)

        val cleanUrl = request.url.newBuilder().fragment(null).build()
        val response = chain.proceed(request.newBuilder().url(cleanUrl).build())
        if (!response.isSuccessful) return response

        val bytes = response.body.bytes()
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: return response.withBody(bytes)

        val out = try {
            Scramble.descramble(decoded, key)
        } catch (_: Exception) {
            decoded
        }
        if (out != decoded) {
            decoded.recycle()
        }

        val bos = ByteArrayOutputStream()
        out.compress(Bitmap.CompressFormat.PNG, 100, bos)
        out.recycle()
        return response.withBody(bos.toByteArray(), "image/png")
    }

    private fun Response.withBody(bytes: ByteArray, mime: String = "image/*"): Response = newBuilder()
        .body(bytes.toResponseBody(mime.toMediaType()))
        .build()
}
