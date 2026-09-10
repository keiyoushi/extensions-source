package eu.kanade.tachiyomi.extension.all.lunaranime

import android.util.Base64
import keiyoushi.utils.extractNextJs
import org.jsoup.nodes.Document
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

class LunarMintedToken(val token: String, val nonce: String)

class LunarDecryptor(private val serenity: LunarSerenity) {

    // ============================== Seeds ==============================

    fun extractSeeds(document: Document): LunarSeeds = document.extractNextJs<LunarSeedPropsDto>(LunarSeedPropsDto::matches)?.toSeeds()
        ?: error("Failed to find payload seeds")

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

    private fun randomString(length: Int) = String(CharArray(length) { ALPHABET[Random.nextInt(ALPHABET.length)] })

    private fun List<Int>.toByteArray() = ByteArray(size) { this[it].toByte() }

    private fun ByteArray.base64Url(): String = Base64.encodeToString(this, Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE)

    companion object {
        private const val ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        private const val NONCE_LENGTH = 12
        private const val SEPARATOR = "\u0001"
        private const val THUMBPRINT_SEPARATOR = "\u0002"
    }
}
