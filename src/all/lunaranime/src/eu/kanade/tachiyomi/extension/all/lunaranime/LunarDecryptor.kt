package eu.kanade.tachiyomi.extension.all.lunaranime

import android.util.Base64
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Response
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.experimental.xor
import kotlin.random.Random

class LunarDecryptor(
    private val client: OkHttpClient,
    private val apiUrl: String,
) {

    private val headers = Headers.Builder()
        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        .add("Referer", "https://lunarx.to/")
        .build()

    fun getChapterImages(slug: String, chapterNum: String, lang: String): List<String> {
        val chapterWebUrl = "https://lunarx.to/manga/$slug/$chapterNum?lang=$lang"
        val webRequest = GET(chapterWebUrl, headers)
        val seedObjs = runCatching {
            client.newCall(webRequest).execute().extractSeeds()
        }.getOrDefault(emptyList())

        val (token, seed0) = if (seedObjs.size >= 2) {
            val s0 = generateRctxFrom(seedObjs[0])
            val s1 = generateRctxFrom(seedObjs[1])
            val tok = generateToken(s0, s1, slug, chapterNum)
            tok to s0
        } else {
            val s0 = "seed0"
            val s1 = "seed1"
            val tok = mintTokenFallback(slug, chapterNum, s0, s1)
            tok to s0
        }

        val pageListResponse = fetchSessionData(token, lang)

        pageListResponse.data?.images?.takeIf { it.isNotEmpty() }?.let { images ->
            if (images.none { it.contains("unknown") }) {
                return images
            }
        }

        val sessionDataB64 = pageListResponse.data?.sessionData
        if (!sessionDataB64.isNullOrEmpty()) {
            runCatching {
                val finalJson = decryptSessionImages(sessionDataB64, seed0, chapterNum)
                val decrypted = finalJson.parseAs<LunarPageListDecrypted>().data.images
                if (decrypted.isNotEmpty()) return decrypted
            }
        }

        return pageListResponse.data?.images ?: emptyList()
    }

    private fun sha256Bytes(s: String): ByteArray = MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.ISO_8859_1))

    private fun generateXorKeyFallback(seed0: String, seed1: String): ByteArray {
        val r = sha256Bytes("$seed0\u0001$seed1")
        val maxLen = maxOf(seed0.length, seed1.length)
        val res = ByteArray(maxLen)
        for (n in 0 until maxLen) {
            val eChar = seed0[n % seed0.length].code
            val tChar = seed1[n % seed1.length].code
            val rByte = r[n % 32].toInt() and 0xFF
            res[n] = (eChar xor tChar xor rByte xor (83 * n + 29)).toByte()
        }
        return res
    }

    private fun mintTokenFallback(slug: String, chapterNum: String, seed0: String, seed1: String): String {
        val xorKey = generateXorKeyFallback(seed0, seed1)
        val timestamp = (System.currentTimeMillis() / 1000).toString(16)
        val rand12 = (1..12).map { RAND_ALPHABET[Random.nextInt(RAND_ALPHABET.length)] }.joinToString("")
        val rand6 = (1..6).map { RAND_ALPHABET[Random.nextInt(RAND_ALPHABET.length)] }.joinToString("")
        val payload = "$timestamp|$rand12|$slug|$chapterNum|$rand6"

        val n = Random.nextInt(256)
        val a = ByteArray(payload.length + 1)
        a[0] = n.toByte()
        val keyLen = xorKey.size
        for (i in payload.indices) {
            val code = payload[i].code
            val k = xorKey[(i + n) % keyLen].toInt() and 0xFF
            val enc = (code xor k xor (n + 83 * i)) and 0xFF
            a[i + 1] = enc.toByte()
        }

        return Base64.encodeToString(a, Base64.NO_WRAP)
            .replace('+', '-').replace('/', '_').trimEnd('=')
    }

    private fun fetchSessionData(token: String, lang: String): LunarPageListResponse {
        val url = "$apiUrl/api/manga/r/$token?lang=$lang"
        val response = client.newCall(GET(url, headers)).execute()
        if (!response.isSuccessful) error("Failed decrypting with ${response.code} while fetching session_data")
        return response.parseAs<LunarPageListResponse>()
    }

    private val dictRegex = Regex("""\{[^{}]*\}""")

    private fun isSeedMap(map: Map<String, String>): Boolean {
        val seedEntry = map.entries.firstOrNull { it.value.startsWith("=") || it.value.endsWith("=") } ?: return false
        val reversedVal = seedEntry.value.reversed()
        return try {
            val decoded = String(Base64.decode(reversedVal.padEnd((reversedVal.length + 3) / 4 * 4, '='), Base64.DEFAULT))
            decoded.contains('.') && decoded.split('.').firstOrNull()?.all { it.isLetterOrDigit() } == true
        } catch (_: Exception) {
            false
        }
    }

    fun Response.extractSeeds(): List<Map<String, String>> {
        val doc = asJsoup()
        val html = doc.outerHtml().replace("\\\"", "\"").replace("\\\\", "\\")
        val seedObjects = mutableListOf<Map<String, String>>()

        for (dictStr in dictRegex.findAll(html)) {
            try {
                val map = dictStr.value.parseAs<Map<String, String>>()
                if (isSeedMap(map) && !seedObjects.contains(map)) {
                    seedObjects.add(map)
                }
            } catch (_: Exception) { }
        }

        return seedObjects
    }

    private fun findSeedKeyVal(data: Map<String, String>): Pair<String, String> {
        val entry = data.entries.first { it.value.startsWith("=") || it.value.endsWith("=") }
        return entry.key to entry.value.reversed()
    }

    private fun decodeReversedBase64(reversed: String) = String(Base64.decode(reversed.padEnd((reversed.length + 3) / 4 * 4, '='), Base64.DEFAULT))

    private fun generateRctxFrom(seedObj: Map<String, String>): String {
        val (_, reversedB64) = findSeedKeyVal(seedObj)
        val (xorKey, hexStr) = decodeReversedBase64(reversedB64).split('.')
            .let { parts -> parts[0].toInt(16) to parts.drop(1).joinToString("") { seedObj[it] ?: "" } }

        val aStr = hexStr.chunked(2).mapIndexed { i, h ->
            ((h.toInt(16) xor ((xorKey + i * 7 + 3) and 0xFF)).toChar())
        }.joinToString("")
        if (aStr.isEmpty()) return ""

        val rand = Random(aStr.length.toLong())
        val h = IntArray(256) { it }.apply {
            for (i in 255 downTo 1) {
                val j = rand.nextInt(i + 1)
                this[i] = this[j].also { this[j] = this[i] }
            }
        }
        val s = IntArray(256) { i -> h.indexOf(i) }
        val u = IntArray(aStr.length) { rand.nextInt(256) }
        val d = aStr.map { it.code }.toMutableList()

        repeat(3) { round ->
            d.indices.forEach { t ->
                d[t] = d[t] xor u[(t + 7 * round) % u.size]
                d[t] = h[d[t]]
                val shift = (t + 3 * round + 1) % 7 + 1
                d[t] = ((d[t] shl shift) or (d[t] shr (8 - shift))) and 0xFF
            }
            for (t in 1 until d.size) d[t] = d[t] xor d[t - 1]
        }

        val e = d.toMutableList()
        for (round in 2 downTo 0) {
            for (t in e.size - 1 downTo 1) e[t] = e[t] xor e[t - 1]
            e.indices.forEach { t ->
                var shift = (t + 3 * round + 1) % 7 + 1
                e[t] = ((e[t] shr shift) or (e[t] shl (8 - shift))) and 0xFF
                e[t] = s[e[t]]
                e[t] = e[t] xor u[(t + 7 * round) % u.size]
            }
        }
        return e.joinToString("") { it.toChar().toString() }
    }

    private fun xorStrings(first: String, second: String): ByteArray = ByteArray(maxOf(first.length, second.length)) { i ->
        (first[i % first.length].code.toByte() xor second[i % second.length].code.toByte())
    }

    private fun generateToken(rctx0: String, rctx1: String, slug: String, index: String): String {
        val xorKey = xorStrings(rctx0, rctx1)
        val timestamp = (System.currentTimeMillis() / 1000).toString(16)
        val rand = (1..8).map { RAND_ALPHABET[Random.nextInt(RAND_ALPHABET.length)] }.joinToString("")
        val payload = "$timestamp|$rand|$slug|$index"
        val encrypted = payload.mapIndexed { i, c ->
            (c.code xor xorKey[i % xorKey.size].toInt()).toByte()
        }.toByteArray()
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
            .replace('+', '-').replace('/', '_').trimEnd('=')
    }

    private fun decryptSessionImages(sessionDataB64: String, rctx0: String, chapterNum: String): String {
        val ciphertext = Base64.decode(sessionDataB64.replace('-', '+').replace('_', '/').padEnd((sessionDataB64.length + 3) / 4 * 4, '='), Base64.DEFAULT)
        val keyStr = "$rctx0\u0001$chapterNum"
        val key = MessageDigest.getInstance("SHA-256").digest(keyStr.toByteArray())
        Cipher.getInstance("AES/CBC/PKCS5Padding").apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(ByteArray(16)))
            return String(doFinal(ciphertext), Charsets.UTF_8)
        }
    }

    companion object {
        private const val RAND_ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    }
}
