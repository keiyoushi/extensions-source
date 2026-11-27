package eu.kanade.tachiyomi.extension.vi.dualeotruyen

import android.util.Base64
import keiyoushi.utils.decodeHex
import keiyoushi.utils.hmacSha256
import keiyoushi.utils.jsonInstance
import keiyoushi.utils.sha256
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * The config lives in a `<script>` tag carrying exactly 3 attributes named
 * `data-[a-z][a-z0-9]{4,6}`. Sorted by attribute name, the concatenated values
 * form a base64url blob: first byte is the initial LCG state, each following
 * byte is XORed with `state = state * 37 + 151`. The plaintext is a positional
 * JSON array of 12 items.
 */
class V7Config private constructor(
    val multiplier: Int,
    val imageClass: String,
    keyHex: String,
    val hosts: List<String>,
    val increment: Int,
    val attrA: String,
    val namePrefix: String,
    val canvasClass: String,
    val attrB: String,
    val seedMask: Int,
    val attrE: String,
    val canvasTokens: List<String>,
) {
    val secret: ByteArray = keyHex.decodeHex()

    private fun unmaskToken(token: String, mask: Int, mul: Int, add: Int, mix: Int): String {
        val bytes = base64UrlDecode(token)
        require(bytes.size >= 3) { "bad token length" }
        var state = (bytes[0].toInt() and 0xFF) xor mask
        val decoded = ByteArray(bytes.size - 1)
        for (i in decoded.indices) {
            state = (state * mul + add) and 0xFF
            decoded[i] = ((bytes[i + 1].toInt() and 0xFF) xor state xor ((i * mix) and 0xFF)).toByte()
        }
        val result = decoded.decodeToString()
        val hostIndex = result.firstOrNull()?.digitToIntOrNull() ?: throw IllegalArgumentException("invalid decoded url")
        val host = hosts.getOrNull(hostIndex) ?: throw IllegalArgumentException("invalid host index")
        if (!result[0].isDigit() || result.getOrNull(1) != '/' || !result.contains("/$namePrefix")) {
            throw IllegalArgumentException("invalid decoded url")
        }
        return host + result.substring(1)
    }

    fun imageToken(element: Element): String {
        val token = element.attr(attrB) + element.attr(attrE) + element.attr(attrA)
        return unmaskToken(token, seedMask, multiplier, increment, 13)
    }

    fun canvasToken(canvasIndex: Int): String {
        val token = canvasTokens.getOrNull(canvasTokens.size - 1 - canvasIndex)
            ?: throw IllegalArgumentException("missing canvas token")
        return unmaskToken(token, seedMask xor 0x5a, 45, 99, 29)
    }

    fun decodeObfuscatedUrl(input: String): String? {
        val url = input.toHttpUrl()
        val fileName = url.pathSegments.last()
        if (!fileName.startsWith(namePrefix)) return null
        val extension = fileName.substringAfterLast('.', "")
        val encoded = fileName.substringBeforeLast('.').removePrefix(namePrefix)
        val raw = base64UrlDecode(encoded)
        if (raw.size < 23) return null

        val mac = raw.copyOfRange(0, 12)
        val nonce = raw.copyOfRange(12, 22)
        val cipher = raw.copyOfRange(22, raw.size)

        val stream = ByteArray(cipher.size)
        var filled = 0
        var counter = 0
        while (filled < cipher.size) {
            val material = nonce + secret + byteArrayOf((counter ushr 8).toByte(), counter.toByte())
            val block = material.sha256()
            val count = minOf(block.size, cipher.size - filled)
            block.copyInto(stream, filled, 0, count)
            filled += count
            counter++
        }
        val nameBytes = ByteArray(cipher.size) { i -> (cipher[i].toInt() xor stream[i].toInt()).toByte() }
        val actualMac = (nonce + nameBytes).hmacSha256(secret)
        if (!actualMac.copyOf(12).contentEquals(mac)) return null

        val name = nameBytes.decodeToString()
        return url.newBuilder()
            .setPathSegment(url.pathSegments.lastIndex, "$name.$extension")
            .build()
            .toString()
    }

    companion object {
        private val CONFIG_ATTR = Regex("data-[a-z][a-z0-9]{4,6}")

        fun base64UrlDecode(value: String): ByteArray = Base64.decode(value, Base64.URL_SAFE)

        fun extract(document: Document): V7Config? {
            for (script in document.select("script")) {
                val attributes = script.attributes()
                    .filter { CONFIG_ATTR.matches(it.key) }
                if (attributes.size != 3) continue

                val concat = attributes
                    .sortedBy { it.key }
                    .joinToString("") { it.value }
                val config = runCatching { parse(concat) }.getOrNull() ?: continue
                return config
            }
            return null
        }

        private fun parse(concat: String): V7Config {
            val bin = base64UrlDecode(concat)
            var state = bin[0].toInt() and 0xFF
            val bytes = ByteArray(bin.size - 1)
            for (i in 1 until bin.size) {
                state = (state * 37 + 151) and 0xFF
                bytes[i - 1] = ((bin[i].toInt() and 0xFF) xor state).toByte()
            }
            val arr = jsonInstance.parseToJsonElement(bytes.decodeToString()).jsonArray
            return V7Config(
                multiplier = arr[0].jsonPrimitive.int,
                imageClass = arr[1].jsonPrimitive.content,
                keyHex = arr[2].jsonPrimitive.content,
                hosts = arr[3].jsonArray.map { it.jsonPrimitive.content },
                increment = arr[4].jsonPrimitive.int,
                attrA = arr[5].jsonPrimitive.content,
                namePrefix = arr[6].jsonPrimitive.content,
                canvasClass = arr[7].jsonPrimitive.content,
                attrB = arr[8].jsonPrimitive.content,
                seedMask = arr[9].jsonPrimitive.int,
                attrE = arr[10].jsonPrimitive.content,
                canvasTokens = arr[11].jsonArray.map { it.jsonPrimitive.content },
            )
        }
    }
}
