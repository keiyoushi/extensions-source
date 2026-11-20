package eu.kanade.tachiyomi.extension.all.lunaranime

import android.util.Base64
import keiyoushi.utils.aesCbc
import keiyoushi.utils.aesDecrypt
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.sha256
import org.jsoup.nodes.Document
import java.security.GeneralSecurityException
import kotlin.random.Random
import kotlin.time.Clock

class LunarDecryptor(private val serenity: LunarSerenity) {

    fun extractSeeds(document: Document): LunarSeeds = document.extractNextJs<LunarSeedPropsDto>(LunarSeedPropsDto::matches)?.toSeeds()
        ?: error("Failed to find payload seeds")

    fun newNonce(): String = randomString(NONCE_LENGTH)

    /**
     * `mint()` in the site's bundle: a timestamped payload naming the chapter, stream-encrypted
     * with a key blended from both seeds. [unpack] takes the same nonce.
     */
    fun mint(seeds: LunarSeeds, slug: String, chapter: String, nonce: String): String {
        val key = blend(seeds.axis, seeds.pitch)
        require(key.isNotEmpty()) { "Failed to derive chapter key" }

        val timestamp = Clock.System.now().epochSeconds.toString(16)
        val payload = "$timestamp|$nonce|$slug|$chapter|${randomString(6)}"

        val offset = Random.nextInt(256)
        val out = ArrayList<Int>(payload.length + 1)
        out.add(offset)
        payload.forEachIndexed { i, char ->
            out.add(char.code xor key[(i + offset) % key.size] xor (offset + 83 * i and 0xFF) and 0xFF)
        }

        return out.toByteArray().base64Url()
    }

    private fun blend(axis: String, pitch: String): IntArray {
        val digest = (axis + SEPARATOR + pitch).sha256()
        val size = maxOf(axis.length, pitch.length)

        return IntArray(size) { i ->
            axis[i % axis.length].code xor
                pitch[i % pitch.length].code xor
                (digest[i % 32].toInt() and 0xFF) xor
                (83 * i + 29 and 0xFF) and 0xFF
        }
    }

    /**
     * The response body is AES-CBC over a key derived from the first seed, the nonce that was
     * minted into the token, and - when the server enforces it - the Serenity key thumbprint.
     */
    fun unpack(sessionData: String, seeds: LunarSeeds, nonce: String): String {
        val ciphertext = Base64.decode(
            sessionData.replace('-', '+').replace('_', '/'),
            Base64.DEFAULT,
        )
        val base = seeds.axis + SEPARATOR + nonce

        for (material in listOf(base + THUMBPRINT_SEPARATOR + serenity.thumbprint, base)) {
            val plaintext = try {
                ciphertext.aesDecrypt(aesCbc(material.sha256(), ByteArray(16))).decodeToString()
            } catch (_: GeneralSecurityException) {
                continue
            }

            if (plaintext.startsWith("{") || plaintext.startsWith("[")) return plaintext
        }
        error("Failed to decrypt chapter session data")
    }

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
