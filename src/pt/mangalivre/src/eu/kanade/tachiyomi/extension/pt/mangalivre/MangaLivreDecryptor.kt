package eu.kanade.tachiyomi.extension.pt.mangalivre

import android.util.Base64
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.get
import keiyoushi.utils.parseAs
import keiyoushi.utils.readIntBigEndian
import keiyoushi.utils.readIntLittleEndian
import keiyoushi.utils.stringOrNull
import kotlinx.serialization.json.JsonElement
import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import java.security.MessageDigest
import java.time.LocalDate
import java.time.ZoneOffset

class MangaLivreDecryptor(
    private val baseUrl: String,
    private val client: OkHttpClient,
    private val headers: Headers,
) {

    private val baseUrlHost = baseUrl.toHttpUrl().host

    @Volatile private var constants = Constants(DEFAULT_HASH_INPUT_SUFFIX, DEFAULT_ENC_KEY)

    private var lastReloadAt = 0L

    private data class Constants(val hashInputSuffix: String, val encKey: String)

    private class Payload(val salt: ByteArray, val ciphertext: ByteArray)

    fun decrypt(cipherWrapperBody: String, dataKey: String): String? = cipherWrapperBody.toPayload(dataKey)?.decrypt(constants)

    fun reloadConstantsAndDecrypt(readerPath: String, cipherWrapperBody: String, dataKey: String): String? {
        val payload = cipherWrapperBody.toPayload(dataKey) ?: return null
        synchronized(this) {
            payload.decrypt(constants)?.let { return it }

            val now = System.currentTimeMillis()
            if (now - lastReloadAt < RELOAD_COOLDOWN_MS) return null
            lastReloadAt = now

            for (scriptUrl in fetchScriptUrls(readerPath)) {
                val reloaded = fetchScript(scriptUrl)?.extractConstants(payload) ?: continue
                constants = reloaded
                return payload.decrypt(reloaded)
            }
        }
        return null
    }

    private fun fetchScriptUrls(readerPath: String): List<HttpUrl> = runCatching {
        val request = GET(baseUrl + readerPath, headers).newBuilder()
            .cacheControl(CacheControl.FORCE_NETWORK)
            .build()
        client.newCall(request).execute().use { it.asJsoup() }
            .select("script[src]")
            .mapNotNull { it.absUrl("src").toHttpUrlOrNull() }
            .filter { it.host == baseUrlHost }
            .distinct()
            .sortedBy { if (it.pathSegments.last().startsWith(BUNDLE_NAME_PREFIX)) 0 else 1 }
            .take(MAX_BUNDLE_CANDIDATES)
    }.getOrDefault(emptyList())

    private fun fetchScript(scriptUrl: HttpUrl): String? = runCatching {
        client.newCall(GET(scriptUrl, headers)).execute().use { response ->
            response.takeIf { it.isSuccessful }?.body?.string()
        }
    }.getOrNull()

    private fun String.extractConstants(payload: Payload): Constants? {
        for (values in constantValueVariants()) {
            values.asConstantCandidates()
                .firstOrNull { payload.looksDecryptable(it) && payload.decrypt(it) != null }
                ?.let { return it }
        }
        return legacyConstants()?.takeIf { payload.decrypt(it) != null }
    }

    /**
     * Ordered value lists to build candidate constants from, cheapest first.
     *
     * The scopes around the hash call cover constants kept as plain literals. Obfuscated ones do
     * not need an anchor at all: values that decode to printable ASCII are rare enough to be
     * collected from the whole script, so the recovery survives the call site being rewritten.
     */
    private fun String.constantValueVariants(): Sequence<List<String>> = sequence {
        for (call in SHA256_CALL_REGEX.findAll(this@constantValueVariants).take(MAX_SHA256_CALL_SITES)) {
            yieldAll(scopeValues(call.range.first))
        }
        yield(decodedValues())
    }

    private fun String.legacyConstants(): Constants? {
        val legacy = EV_CONSTANTS_REGEX.find(this)
        val hostPart = ENV_HOST_REGEX.find(this)?.groupValues?.get(1)
            ?: legacy?.groupValues?.get(1)
            ?: return null
        val antibotPart = ENV_ANTIBOT_REGEX.find(this)?.groupValues?.get(1)
            ?: legacy?.groupValues?.get(2)
            ?: return null
        val encKey = ENV_ENCRYPTION_REGEX.find(this)?.groupValues?.get(1)
            ?: legacy?.groupValues?.get(3)
            ?: return null
        return Constants(hostPart + antibotPart, encKey)
    }

    private fun String.scopeValues(hashIndex: Int): List<List<String>> {
        val searchStart = maxOf(0, hashIndex - FUNCTION_SEARCH_WINDOW)
        val arrowBlock = ARROW_BLOCK_REGEX.findAll(substring(searchStart, hashIndex)).lastOrNull()
        val openingBrace = arrowBlock?.range?.last?.plus(searchStart)
        val scopeStart = openingBrace?.plus(1) ?: searchStart
        val scopeEnd = openingBrace?.let { findMatchingBrace(it) }
            ?: minOf(length, hashIndex + FUNCTION_TAIL_WINDOW)
        val scope = substring(scopeStart, scopeEnd)

        val values = buildList {
            STRING_LITERAL_REGEX.findAll(scope).forEach { match ->
                add(match.range.first to match.groupValues[1].ifEmpty { match.groupValues[2] })
            }
            BYTE_SEQUENCE_REGEX.findAll(scope).forEach { match ->
                match.decodeByteSequence()?.let { add(match.range.first to it) }
            }
        }.inSourceOrder()

        // Candidates are built from contiguous runs, so a decoded value cannot simply be appended
        // next to its encoded form. Each decoding is a separate ordered variant.
        return listOf(values, values.map { it.decodeHexAscii() ?: it })
            .map { it.asConstantValues() }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    private fun String.decodedValues(): List<String> = buildList {
        HEX_LITERAL_REGEX.findAll(this@decodedValues).forEach { match ->
            match.groupValues[1].ifEmpty { match.groupValues[2] }
                .decodeHexAscii()
                ?.let { add(match.range.first to it) }
        }
        BYTE_SEQUENCE_REGEX.findAll(this@decodedValues).forEach { match ->
            match.decodeByteSequence()?.let { add(match.range.first to it) }
        }
    }.inSourceOrder().asConstantValues()

    private fun List<Pair<Int, String>>.inSourceOrder(): List<String> = sortedBy { it.first }.map { it.second }

    private fun List<String>.asConstantValues(): List<String> = filter { it.isConstantCandidate() }.distinct().take(MAX_CONSTANT_VALUES)

    private fun String.findMatchingBrace(openingBrace: Int): Int? {
        var depth = 0
        var quote: Char? = null
        var escaped = false

        for (index in openingBrace until length) {
            val char = this[index]
            if (quote != null) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == quote -> quote = null
                }
                continue
            }

            when (char) {
                '\'', '"', '`' -> quote = char
                '{' -> depth++
                '}' -> if (--depth == 0) return index
            }
        }
        return null
    }

    private fun MatchResult.decodeByteSequence(): String? {
        val tokens = groupValues[1].split(',')
        val codes = tokens.mapNotNull { token ->
            val trimmed = token.trim()
            val code = if (trimmed.startsWith("0x", ignoreCase = true)) {
                trimmed.drop(2).toIntOrNull(16)
            } else {
                trimmed.toIntOrNull()
            }
            code?.takeIf { it in PRINTABLE_ASCII_RANGE }
        }
        return codes.takeIf { it.size == tokens.size }
            ?.map(Int::toChar)
            ?.joinToString("")
    }

    private fun String.isConstantCandidate(): Boolean = length in MIN_CONSTANT_LENGTH..MAX_CONSTANT_LENGTH && all { it.code in PRINTABLE_ASCII_RANGE }

    private fun String.decodeHexAscii(): String? {
        if (length < MIN_HEX_LENGTH || length % 2 != 0) return null
        val codes = chunked(2).map { it.toIntOrNull(16) ?: return null }
        return codes.takeIf { it.all { code -> code in PRINTABLE_ASCII_RANGE } }
            ?.map(Int::toChar)
            ?.joinToString("")
    }

    private fun List<String>.asConstantCandidates(): Sequence<Constants> = sequence {
        for (start in indices) {
            for (end in start until size) {
                val hashInputSuffix = subList(start, end + 1).joinToString("")
                for (keyIndex in indices) {
                    if (keyIndex in start..end) continue
                    yield(Constants(hashInputSuffix, this@asConstantCandidates[keyIndex]))
                }
            }
        }
    }

    private fun String.toPayload(dataKey: String): Payload? = runCatching {
        val ciphertextB64 = parseAs<JsonElement>()[dataKey]?.stringOrNull ?: return null
        val encrypted = Base64.decode(ciphertextB64, Base64.DEFAULT)
        Payload(encrypted.copyOfRange(8, 16), encrypted.copyOfRange(16, encrypted.size))
    }.getOrNull()

    private fun Payload.decrypt(constants: Constants): String? {
        val today = LocalDate.now(ZoneOffset.UTC)
        for (offset in DATE_OFFSETS) {
            val plaintext = String(decryptRabbit(derivePassword(today.plusDays(offset), constants)), Charsets.UTF_8)
            if (plaintext.isValidJson()) return plaintext
        }
        return null
    }

    private fun Payload.looksDecryptable(constants: Constants): Boolean {
        val today = LocalDate.now(ZoneOffset.UTC)
        return DATE_OFFSETS.any { offset ->
            decryptRabbit(derivePassword(today.plusDays(offset), constants), PREVIEW_LENGTH).looksLikeJson()
        }
    }

    private fun Payload.decryptRabbit(password: String, maxBytes: Int = Int.MAX_VALUE): ByteArray {
        val (key, iv) = evpBytesToKey(password.toByteArray(), salt)
        val plaintext = ciphertext.copyOf(minOf(maxBytes, ciphertext.size))
        Rabbit().apply { setup(key, iv) }.crypt(plaintext)
        return plaintext
    }

    private fun ByteArray.looksLikeJson(): Boolean {
        val start = indexOfFirst { !it.isJsonWhitespace() }.takeIf { it >= 0 } ?: return false
        return (this[start] == '{'.code.toByte() || this[start] == '['.code.toByte()) && all { it.isJsonText() }
    }

    private fun Byte.isJsonWhitespace(): Boolean = toInt() in JSON_WHITESPACE

    private fun Byte.isJsonText(): Boolean = (toInt() and 0xFF) >= 0x20 || isJsonWhitespace()

    private fun String.isValidJson(): Boolean = runCatching { parseAs<JsonElement>() }.isSuccess

    private fun derivePassword(date: LocalDate, constants: Constants): String {
        val toHash = "$date${constants.hashInputSuffix}"
        val hashPart = MessageDigest.getInstance("SHA-256")
            .digest(toHash.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .substring(0, 8)
        return constants.encKey + hashPart
    }

    private fun evpBytesToKey(password: ByteArray, salt: ByteArray, keyLen: Int = 16, ivLen: Int = 8): Pair<ByteArray, ByteArray> {
        val derived = ByteArray(keyLen + ivLen)
        var derivedPos = 0
        var md5Hash = ByteArray(0)
        val md = MessageDigest.getInstance("MD5")
        while (derivedPos < derived.size) {
            md.reset()
            if (md5Hash.isNotEmpty()) md.update(md5Hash)
            md.update(password)
            md.update(salt)
            md5Hash = md.digest()
            val toCopy = minOf(md5Hash.size, derived.size - derivedPos)
            System.arraycopy(md5Hash, 0, derived, derivedPos, toCopy)
            derivedPos += toCopy
        }
        return Pair(derived.copyOfRange(0, keyLen), derived.copyOfRange(keyLen, keyLen + ivLen))
    }

    companion object {
        private const val DEFAULT_HASH_INPUT_SUFFIX = "toonlivre.net::y8q2_4k9_w"
        private const val DEFAULT_ENC_KEY = "Vortex-Blade-Nexus4"

        private const val RELOAD_COOLDOWN_MS = 30_000L
        private const val FUNCTION_SEARCH_WINDOW = 4_000
        private const val FUNCTION_TAIL_WINDOW = 1_000
        private const val MAX_BUNDLE_CANDIDATES = 8
        private const val MAX_SHA256_CALL_SITES = 8
        private const val MAX_CONSTANT_VALUES = 12
        private const val MIN_CONSTANT_LENGTH = 3
        private const val MAX_CONSTANT_LENGTH = 128
        private const val MIN_HEX_LENGTH = 6
        private const val PREVIEW_LENGTH = 64
        private const val BUNDLE_NAME_PREFIX = "index-"
        private val PRINTABLE_ASCII_RANGE = 0x20..0x7E
        private val JSON_WHITESPACE = listOf(0x09, 0x0A, 0x0D, 0x20)
        private val DATE_OFFSETS = longArrayOf(0, -1, 1)
        private val ARROW_BLOCK_REGEX = Regex("""=>\s*\{""")
        private val SHA256_CALL_REGEX = Regex("""(?:\.|\[\s*["'])SHA256""")
        private val STRING_LITERAL_REGEX = Regex(""""([^"\\]*)"|'([^'\\]*)'""")
        private val HEX_LITERAL_REGEX = Regex(""""([\da-fA-F]{$MIN_HEX_LENGTH,})"|'([\da-fA-F]{$MIN_HEX_LENGTH,})'""")
        private val BYTE_SEQUENCE_REGEX = Regex(
            """(?:\[\s*|\(\s*)((?:(?:0[xX][\da-fA-F]+|\d{1,3})\s*,\s*){2,}(?:0[xX][\da-fA-F]+|\d{1,3}))\s*[\])]""",
        )

        private val EV_CONSTANTS_REGEX = Regex(
            """toISOString\(\)\.split\("T"\)\[0]\s*,\s*\w+\s*=\s*"([^"]+)"\s*,\s*\w+\s*=\s*"([^"]+)"\s*,\s*\w+\s*=\s*"([^"]+)""",
        )
        private val ENV_HOST_REGEX = Regex("""VITE_HOSTNAME_PART\s*:\s*"([^"]+)"""")
        private val ENV_ANTIBOT_REGEX = Regex("""VITE_ANTIBOT\s*:\s*"([^"]+)"""")
        private val ENV_ENCRYPTION_REGEX = Regex("""VITE_ENCRYPTION_KEY\s*:\s*"([^"]+)"""")
    }
}

// Rabbit stream cipher (CryptoJS-compatible). Ported from KuroMangasDecryptor.kt — same site template.
private class Rabbit {
    val x = IntArray(8)
    val c = IntArray(8)
    var b = 0

    fun setup(key: ByteArray, iv: ByteArray) {
        val kw = IntArray(4)
        for (i in 0 until 4) {
            kw[i] = key.readIntLittleEndian(i * 4)
        }

        x[0] = kw[0]
        x[1] = (kw[3] shl 16) or ((kw[2] ushr 16) and 0xFFFF)
        x[2] = kw[1]
        x[3] = (kw[0] shl 16) or ((kw[3] ushr 16) and 0xFFFF)
        x[4] = kw[2]
        x[5] = (kw[1] shl 16) or ((kw[0] ushr 16) and 0xFFFF)
        x[6] = kw[3]
        x[7] = (kw[2] shl 16) or ((kw[1] ushr 16) and 0xFFFF)

        c[0] = (kw[2] shl 16) or ((kw[2] ushr 16) and 0xFFFF)
        c[1] = (kw[0] and 0xFFFF0000.toInt()) or (kw[1] and 0xFFFF)
        c[2] = (kw[3] shl 16) or ((kw[3] ushr 16) and 0xFFFF)
        c[3] = (kw[1] and 0xFFFF0000.toInt()) or (kw[2] and 0xFFFF)
        c[4] = (kw[0] shl 16) or ((kw[0] ushr 16) and 0xFFFF)
        c[5] = (kw[2] and 0xFFFF0000.toInt()) or (kw[3] and 0xFFFF)
        c[6] = (kw[1] shl 16) or ((kw[1] ushr 16) and 0xFFFF)
        c[7] = (kw[3] and 0xFFFF0000.toInt()) or (kw[0] and 0xFFFF)

        b = 0

        repeat(4) { nextState() }

        for (i in 0 until 8) {
            c[i] = c[i] xor x[(i + 4) and 7]
        }

        if (iv.isNotEmpty()) {
            val iv0 = iv.readIntBigEndian(0)
            val iv1 = iv.readIntBigEndian(4)

            fun swap(w: Int) = ((w and 0xFF) shl 24) or
                ((w and 0xFF00) shl 8) or
                ((w and 0xFF0000) ushr 8) or
                ((w ushr 24) and 0xFF)

            val i0 = swap(iv0)
            val i2 = swap(iv1)
            val i1 = (i0 ushr 16) or (i2 and 0xFFFF0000.toInt())
            val i3 = ((i2 shl 16) or (i0 and 0x0000FFFF))

            c[0] = c[0] xor i0
            c[1] = c[1] xor i1
            c[2] = c[2] xor i2
            c[3] = c[3] xor i3
            c[4] = c[4] xor i0
            c[5] = c[5] xor i1
            c[6] = c[6] xor i2
            c[7] = c[7] xor i3

            repeat(4) { nextState() }
        }
    }

    fun crypt(data: ByteArray) {
        val wordsSize = (data.size + 3) / 4
        val words = IntArray(wordsSize)

        for (i in 0 until wordsSize) {
            var word = 0
            for (j in 0 until 4) {
                val byteIdx = i * 4 + j
                if (byteIdx < data.size) {
                    word = word or ((data[byteIdx].toInt() and 0xFF) shl (j * 8))
                }
            }
            words[i] = word
        }

        var idx = 0
        while (idx < words.size) {
            nextState()

            val (s0, s1, s2, s3) = keystreamBlock()

            if (idx < words.size) words[idx] = words[idx] xor s0
            if (idx + 1 < words.size) words[idx + 1] = words[idx + 1] xor s1
            if (idx + 2 < words.size) words[idx + 2] = words[idx + 2] xor s2
            if (idx + 3 < words.size) words[idx + 3] = words[idx + 3] xor s3

            idx += 4
        }

        for (byteIdx in 0 until data.size) {
            val wordIdx = byteIdx / 4
            val shift = (byteIdx % 4) * 8
            data[byteIdx] = ((words[wordIdx] ushr shift) and 0xFF).toByte()
        }
    }

    fun keystreamBlock(): Quadruple {
        val s0 = (x[0] xor (x[5] ushr 16) xor (x[3] shl 16))
        val s1 = (x[2] xor (x[7] ushr 16) xor (x[5] shl 16))
        val s2 = (x[4] xor (x[1] ushr 16) xor (x[7] shl 16))
        val s3 = (x[6] xor (x[3] ushr 16) xor (x[1] shl 16))
        return Quadruple(s0, s1, s2, s3)
    }

    fun nextState() {
        val cOld = c.copyOf()

        c[0] = c[0] + 0x4D34D34D + b
        c[1] = c[1] + 0xD34D34D3u.toInt() + (if (unsignedLessThan(c[0], cOld[0])) 1 else 0)
        c[2] = c[2] + 0x34D34D34 + (if (unsignedLessThan(c[1], cOld[1])) 1 else 0)
        c[3] = c[3] + 0x4D34D34D + (if (unsignedLessThan(c[2], cOld[2])) 1 else 0)
        c[4] = c[4] + 0xD34D34D3u.toInt() + (if (unsignedLessThan(c[3], cOld[3])) 1 else 0)
        c[5] = c[5] + 0x34D34D34 + (if (unsignedLessThan(c[4], cOld[4])) 1 else 0)
        c[6] = c[6] + 0x4D34D34D + (if (unsignedLessThan(c[5], cOld[5])) 1 else 0)
        c[7] = c[7] + 0xD34D34D3u.toInt() + (if (unsignedLessThan(c[6], cOld[6])) 1 else 0)
        b = if (unsignedLessThan(c[7], cOld[7])) 1 else 0

        val g = IntArray(8)
        for (i in 0 until 8) {
            val gx = x[i] + c[i]
            val ga = gx and 0xFFFF
            val gb = (gx ushr 16) and 0xFFFF
            val gh = ((((ga * ga) ushr 17) + ga * gb) ushr 15) + gb * gb

            val gl = (((gx.toLong() and 0xFFFF0000L)) * gx) + (((gx.toLong() and 0x0000FFFFL)) * gx)
            g[i] = (gh xor (gl and 0xFFFFFFFFL).toInt())
        }

        x[0] = g[0] + ((g[7] shl 16) or (g[7] ushr 16)) + ((g[6] shl 16) or (g[6] ushr 16))
        x[1] = g[1] + ((g[0] shl 8) or (g[0] ushr 24)) + g[7]
        x[2] = g[2] + ((g[1] shl 16) or (g[1] ushr 16)) + ((g[0] shl 16) or (g[0] ushr 16))
        x[3] = g[3] + ((g[2] shl 8) or (g[2] ushr 24)) + g[1]
        x[4] = g[4] + ((g[3] shl 16) or (g[3] ushr 16)) + ((g[2] shl 16) or (g[2] ushr 16))
        x[5] = g[5] + ((g[4] shl 8) or (g[4] ushr 24)) + g[3]
        x[6] = g[6] + ((g[5] shl 16) or (g[5] ushr 16)) + ((g[4] shl 16) or (g[4] ushr 16))
        x[7] = g[7] + ((g[6] shl 8) or (g[6] ushr 24)) + g[5]
    }

    fun unsignedLessThan(a: Int, b: Int) = a.toUInt() < b.toUInt()

    data class Quadruple(val s0: Int, val s1: Int, val s2: Int, val s3: Int)
}
