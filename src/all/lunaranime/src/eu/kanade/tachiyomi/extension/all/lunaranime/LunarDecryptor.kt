package eu.kanade.tachiyomi.extension.all.lunaranime

import android.util.Base64
import keiyoushi.utils.parseAs
import org.jsoup.nodes.Document
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

/**
 * The reader page ships two seed strings hidden in the flight payload, split across the props
 * of a decoy-heavy component. They are the only client-side input to both the chapter token
 * and the key its response is encrypted with.
 */
class LunarSeeds(val axis: String, val pitch: String)

class LunarMintedToken(val token: String, val nonce: String)

class LunarDecryptor(private val serenity: LunarSerenity) {

    // ============================== Seeds ==============================

    fun extractSeeds(document: Document): LunarSeeds {
        // The payload is streamed in chunks that can split an object down the middle, so the
        // whole flight has to be stitched back together before anything is matched against it.
        val flight = document.select("script:not([src])").joinToString("") { script ->
            nextFPushRegex.findAll(script.data()).joinToString("") { it.groupValues[1].parseAs<String>() }
        }

        for (candidate in flatObjectRegex.findAll(flight)) {
            val props = try {
                candidate.value.parseAs<Map<String, String>>()
            } catch (_: Exception) {
                continue
            }
            seedsFrom(props)?.let { return it }
        }
        error("Failed to find payload seeds")
    }

    private fun seedsFrom(props: Map<String, String>): LunarSeeds? {
        for ((key, value) in props) {
            val header = parseHeader(key, value) ?: continue
            val bytes = header.decode(props) ?: continue

            if (bytes.size < HEADER_SIZE) continue
            if (bytes[0] != 167 || bytes[1] != 62 || bytes[2] != 145) return null

            val axisLength = bytes[3] shl 8 or bytes[4]
            val pitchLength = bytes[5] shl 8 or bytes[6]
            if (axisLength <= 0 || pitchLength <= 0 || HEADER_SIZE + axisLength + pitchLength > bytes.size) return null

            return LunarSeeds(
                axis = bytes.toLatin1(HEADER_SIZE, axisLength),
                pitch = bytes.toLatin1(HEADER_SIZE + axisLength, pitchLength),
            )
        }
        return null
    }

    /**
     * The prop holding the header is found by key: its value is reversed base64, masked with a
     * rolling key derived from the prop's own name.
     */
    private fun parseHeader(key: String, value: String): SeedHeader? {
        val decoded = try {
            Base64.decode(value.reversed(), Base64.DEFAULT)
        } catch (_: Exception) {
            return null
        }

        var keyHash = 0
        for (char in key) keyHash = 31 * keyHash + char.code and 0xFF

        val unmasked = String(
            CharArray(decoded.size) { i ->
                ((decoded[i].toInt() and 0xFF) xor (keyHash + 37 * i and 0xFF)).toChar()
            },
        )

        val parts = unmasked.split("|")
        if (parts.size != 6 || parts[0] != "3") return null

        val seed = parts[1].toIntOrNull(16) ?: return null
        val multiplier = parts[2].toIntOrNull(16) ?: return null
        val increment = parts[3].toIntOrNull(16) ?: return null

        val programText = parts[4]
        if (programText.isEmpty() || programText.length % 3 != 0) return null

        val program = buildList {
            for (i in programText.indices step 3) {
                val op = programText.substring(i, i + 1).toIntOrNull(16) ?: return null
                val arg = programText.substring(i + 1, i + 3).toIntOrNull(16) ?: return null
                if (op > 7) return null
                add(op to arg)
            }
        }

        val names = parts[5].split(".").filter { it.isNotEmpty() }
        if (names.isEmpty()) return null

        return SeedHeader(seed, multiplier, increment, program, names)
    }

    private class SeedHeader(
        val seed: Int,
        val multiplier: Int,
        val increment: Int,
        val program: List<Pair<Int, Int>>,
        val names: List<String>,
    ) {
        /**
         * The payload is the named props concatenated, then run backwards through the byte
         * program: each byte is chained to the previous one and to an LCG keyed by the header.
         */
        fun decode(props: Map<String, String>): List<Int>? {
            val hex = names.joinToString("") { props[it].orEmpty() }
            if (hex.length < 2 || hex.length % 2 != 0) return null

            val out = ArrayList<Int>(hex.length / 2)
            var state = seed and 0xFF
            var previous = 0

            for (i in hex.indices step 2) {
                val current = hex.substring(i, i + 2).toIntOrNull(16) ?: return null
                state = state * multiplier + increment and 0xFF
                out.add(unprogram(current xor previous, i / 2, state))
                previous = current
            }
            return out
        }

        private fun unprogram(value: Int, index: Int, state: Int): Int {
            var n = value and 0xFF
            for ((op, arg) in program.asReversed()) {
                n = when (op) {
                    0 -> n xor arg
                    1 -> n - arg
                    2 -> {
                        val shift = (arg and 7).takeIf { it != 0 } ?: 1
                        (n and 0xFF) ushr shift or (n shl 8 - shift)
                    }
                    3 -> (n and 15) shl 4 or ((n and 0xFF) ushr 4)
                    4 -> n xor state
                    5 -> n xor (index * (1 or arg) + arg and 0xFF)
                    6 -> n.inv()
                    else -> arg - n
                } and 0xFF
            }
            return n
        }
    }

    // ============================== Token ==============================

    /**
     * `mint()` in the site's bundle: a timestamped payload naming the chapter, stream-encrypted
     * with a key blended from both seeds. The nonce is folded into the response key, so it has
     * to be kept around for [unpack].
     */
    fun mint(seeds: LunarSeeds, slug: String, chapter: String): LunarMintedToken {
        val key = blend(seeds.axis, seeds.pitch)
        require(key.isNotEmpty()) { "Failed to derive chapter key" }

        val nonce = randomString(NONCE_LENGTH)
        val timestamp = (System.currentTimeMillis() / 1000).toString(16)
        val payload = "$timestamp|$nonce|$slug|$chapter|${randomString(6)}"

        val offset = Random.nextInt(256)
        val out = ArrayList<Int>(payload.length + 1)
        out.add(offset)
        payload.forEachIndexed { i, char ->
            out.add(char.code xor key[(i + offset) % key.size] xor (offset + 83 * i and 0xFF) and 0xFF)
        }

        return LunarMintedToken(out.toByteArray().base64Url(), nonce)
    }

    private fun blend(axis: String, pitch: String): IntArray {
        val digest = sha256(axis + SEPARATOR + pitch)
        val size = maxOf(axis.length, pitch.length)

        return IntArray(size) { i ->
            axis[i % axis.length].code xor
                pitch[i % pitch.length].code xor
                (digest[i % 32].toInt() and 0xFF) xor
                (83 * i + 29 and 0xFF) and 0xFF
        }
    }

    // ============================= Session =============================

    /**
     * The response body is AES-CBC over a key derived from the first seed, the nonce that was
     * minted with the token, and - when the server enforces it - the Serenity key thumbprint.
     */
    fun unpack(sessionData: String, seeds: LunarSeeds, nonce: String): String {
        val ciphertext = Base64.decode(
            sessionData.replace('-', '+').replace('_', '/'),
            Base64.DEFAULT,
        )
        val base = seeds.axis + SEPARATOR + nonce

        for (material in listOf(base + THUMBPRINT_SEPARATOR + serenity.thumbprint, base)) {
            val plaintext = try {
                Cipher.getInstance("AES/CBC/PKCS5Padding").run {
                    init(Cipher.DECRYPT_MODE, SecretKeySpec(sha256(material), "AES"), IvParameterSpec(ByteArray(16)))
                    String(doFinal(ciphertext), Charsets.UTF_8)
                }
            } catch (_: Exception) {
                continue
            }

            if (plaintext.startsWith("{") || plaintext.startsWith("[")) return plaintext
        }
        error("Failed to decrypt chapter session data")
    }

    // ============================ Utilities ============================

    private fun sha256(text: String): ByteArray = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())

    private fun randomString(length: Int) = String(
        CharArray(length) { ALPHABET[Random.nextInt(ALPHABET.length)] },
    )

    private fun List<Int>.toByteArray() = ByteArray(size) { this[it].toByte() }

    private fun ByteArray.base64Url(): String = Base64.encodeToString(this, Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE)

    private fun List<Int>.toLatin1(offset: Int, length: Int) = String(CharArray(length) { this[offset + it].toChar() })

    companion object {
        private const val ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        private const val HEADER_SIZE = 7
        private const val NONCE_LENGTH = 12
        private const val SEPARATOR = "\u0001"
        private const val THUMBPRINT_SEPARATOR = "\u0002"

        private val nextFPushRegex = Regex("""self\.__next_f\.push\(\[1,("(?:[^"\\]|\\.)*")]\)""")
        private val flatObjectRegex = Regex("""\{[^{}]*}""")
    }
}
