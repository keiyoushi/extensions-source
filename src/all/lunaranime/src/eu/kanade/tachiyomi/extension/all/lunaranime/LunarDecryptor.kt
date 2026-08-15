package eu.kanade.tachiyomi.extension.all.lunaranime

import android.util.Base64
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
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

    fun decryptChapterImages(chapterResponse: Response, slug: String, chapterNum: String, lang: String): List<String> {
        val seedObjs = chapterResponse.extractSeeds()
        require(seedObjs.size >= 2) { "Failed to find payload seeds" }

        val rctx0 = generateRctxFrom(seedObjs[0])
        val rctx1 = generateRctxFrom(seedObjs[1])

        val token = generateToken(rctx0, rctx1, slug, chapterNum)

        val sessionDataB64 = fetchSessionData(token, lang)

        val finalJson = decryptSessionImages(sessionDataB64, rctx0)
        return finalJson.parseAs<LunarPageListDecrypted>().data.images
    }

    private fun fetchSessionData(token: String, lang: String): String {
        val url = "$apiUrl/api/manga/r/$token?lang=$lang"
        val response = client.newCall(GET(url)).execute()
        if (!response.isSuccessful) error("Failed decrypting with ${response.code} while fetching session_data")
        val res = response.parseAs<LunarPageListResponse>()
        return res.data?.sessionData ?: error("session_data is empty")
    }

    private val nextFPushRegex = Regex("""self\.__next_f\.push\(\[\s*\d+\s*,\s*(["'])(.*?)\1\s*\]\)""", RegexOption.DOT_MATCHES_ALL)
    private val dictRegex = Regex("""\{[^{}]*\}""")

    private fun unescapeJsString(s: String): String {
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (val next = s[i + 1]) {
                    '"' -> {
                        sb.append('"')
                        i += 2
                    }
                    '\'' -> {
                        sb.append('\'')
                        i += 2
                    }
                    '\\' -> {
                        sb.append('\\')
                        i += 2
                    }
                    '/' -> {
                        sb.append('/')
                        i += 2
                    }
                    'n' -> {
                        sb.append('\n')
                        i += 2
                    }
                    'r' -> {
                        sb.append('\r')
                        i += 2
                    }
                    't' -> {
                        sb.append('\t')
                        i += 2
                    }
                    'b' -> {
                        sb.append('\b')
                        i += 2
                    }
                    'f' -> {
                        sb.append('\u000C')
                        i += 2
                    }
                    'u' -> {
                        if (i + 5 < s.length) {
                            val hex = s.substring(i + 2, i + 6)
                            val code = hex.toIntOrNull(16)
                            if (code != null) {
                                sb.append(code.toChar())
                                i += 6
                                continue
                            }
                        }
                        sb.append(c)
                        i++
                    }
                    else -> {
                        sb.append(next)
                        i += 2
                    }
                }
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    private fun isSeedMap(map: Map<String, String>): Boolean {
        val twoCharEntry = map.entries.firstOrNull { it.key.length == 2 } ?: return false
        val reversedVal = twoCharEntry.value.reversed()
        return try {
            val decoded = String(Base64.decode(reversedVal.padEnd((reversedVal.length + 3) / 4 * 4, '='), Base64.DEFAULT))
            decoded.contains('.')
        } catch (_: Exception) {
            false
        }
    }

    fun Response.extractSeeds(): List<Map<String, String>> {
        val doc = asJsoup()
        val html = doc.outerHtml()
        val seedObjects = mutableListOf<Map<String, String>>()

        fun processDict(dictStr: String) {
            try {
                val map = dictStr.parseAs<Map<String, String>>()
                if (map.keys.any { it.length == 2 } && isSeedMap(map) && !seedObjects.contains(map)) {
                    seedObjects.add(map)
                }
            } catch (_: Exception) { }
        }

        for (match in nextFPushRegex.findAll(html)) {
            val segment = match.groupValues[2]
            val decoded = unescapeJsString(segment)
            for (dictStr in dictRegex.findAll(decoded)) {
                processDict(dictStr.value)
            }
        }

        if (seedObjects.size < 2) {
            for (dictStr in dictRegex.findAll(html)) {
                processDict(dictStr.value)
            }
        }

        return seedObjects
    }

    private val b64urlDecode = { s: String ->
        Base64.decode(s.replace('-', '+').replace('_', '/').padEnd((s.length + 3) / 4 * 4, '='), Base64.DEFAULT)
    }

    private val randAlphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

    // Find 2-char key from flight chunks
    private fun findTwoCharKey(data: Map<String, String>) = data.entries.first { it.key.length == 2 }.let { it.key to it.value.reversed() }

    // Decode reversed base64
    private fun decodeReversedBase64(reversed: String) = String(Base64.decode(reversed.padEnd((reversed.length + 3) / 4 * 4, '='), Base64.DEFAULT))

    // Construct rctx0/rctx1 from seed object with some bit rotations
    private fun generateRctxFrom(seedObj: Map<String, String>): String {
        val (_, reversedB64) = findTwoCharKey(seedObj)
        val (xorKey, hexStr) = decodeReversedBase64(reversedB64).split('.')
            .let { parts -> parts[0].toInt(16) to parts.drop(1).joinToString("") { seedObj[it] ?: "" } }

        val aStr = hexStr.chunked(2).mapIndexed { i, h ->
            ((h.toInt(16) xor ((xorKey + i * 7 + 3) and 0xFF)).toChar())
        }.joinToString("")
        if (aStr.isEmpty()) return ""

        val rand = Random(aStr.length.toLong()) // JS: Math.random() seeded through length
        val h = IntArray(256) { it }.apply {
            // shuffle
            for (i in 255 downTo 1) {
                val j = rand.nextInt(i + 1)
                this[i] = this[j].also { this[j] = this[i] }
            }
        }
        val s = IntArray(256) { i -> h.indexOf(i) }
        val u = IntArray(aStr.length) { rand.nextInt(256) }
        val d = aStr.map { it.code }.toMutableList()

        repeat(3) { round ->
            // 3 forward rounds (JS)
            d.indices.forEach { t ->
                d[t] = d[t] xor u[(t + 7 * round) % u.size]
                d[t] = h[d[t]]
                val shift = (t + 3 * round + 1) % 7 + 1
                d[t] = ((d[t] shl shift) or (d[t] shr (8 - shift))) and 0xFF
            }
            for (t in 1 until d.size) d[t] = d[t] xor d[t - 1]
        }

        val e = d.toMutableList() // 3 reverse rounds
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

    // Js: char code XOR
    private infix fun String.xor(other: String) = ByteArray(maxOf(length, other.length)) { i ->
        (this[i % length].code.toByte() xor other[i % other.length].code.toByte())
    }

    // taken from Js: timestamp|rand|slug|chapterNumber→ base64url
    private fun generateToken(rctx0: String, rctx1: String, slug: String, index: String): String {
        val xorKey = rctx0 xor rctx1
        val timestamp = (System.currentTimeMillis() / 1000).toString(16)
        val rand = (1..8).map { randAlphabet[Random.nextInt(randAlphabet.length)] }.joinToString("")
        val payload = "$timestamp|$rand|$slug|$index"
        val encrypted = payload.mapIndexed { i, c ->
            (c.code xor xorKey[i % xorKey.size].toInt()).toByte()
        }.toByteArray()
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
            .replace('+', '-').replace('/', '_').trimEnd('=')
    }

    // Decrypt session_data (AES‑CBC) as originally, with key being SHA‑256 of rctx0 from token step
    private fun decryptSessionImages(sessionDataB64: String, rctx0: String): String {
        val ciphertext = b64urlDecode(sessionDataB64)
        val key = MessageDigest.getInstance("SHA-256").digest(rctx0.toByteArray())
        Cipher.getInstance("AES/CBC/PKCS5Padding").apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(ByteArray(16)))
            return String(doFinal(ciphertext), Charsets.UTF_8)
        }
    }
}
