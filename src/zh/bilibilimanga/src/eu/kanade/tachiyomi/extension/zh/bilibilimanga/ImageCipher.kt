package eu.kanade.tachiyomi.extension.zh.bilibilimanga

import keiyoushi.lib.ece.Ece
import keiyoushi.utils.newAesCipher
import okio.Buffer
import java.io.IOException
import java.security.PrivateKey
import javax.crypto.KeyAgreement
import javax.crypto.spec.IvParameterSpec

/**
 * Images ImageToken hands out with a `cpx` parameter.
 *
 * Layout: one mode byte, then the image with [MARKER_COUNT] extra bytes inserted at positions seeded by the url's `ts`.
 * Marker bytes 4..68 hold the server's P-256 public key. The head of the image is AES encrypted with the ECDH
 * secret of that key and the reader's (m1), the IV taken from `cpx`; the rest is plain.
 */
object ImageCipher {

    // Buffer.copyTo shares okio segments instead of copying bytes, so only the AES head is ever duplicated.
    fun decrypt(encrypted: Buffer, cpx: ByteArray, ts: Long, privateKey: PrivateKey): Buffer {
        val imageLength = (encrypted.size - 1 - MARKER_COUNT).toInt()
        // markerPositions needs more than MARKER_COUNT positions to pick from.
        if (imageLength <= MARKER_COUNT) throw IOException("Image too short to decrypt")
        val mode = MODES[encrypted[0].toInt()] ?: throw IOException("Unknown image mode ${encrypted[0]}")
        val markers = markerPositions(imageLength, ts - imageLength - MARKER_COUNT)

        // Positions below are in the scrambled body, which starts after the mode byte.
        val serverKey = ByteArray(SERVER_KEY_SIZE) { encrypted[1L + markers[SERVER_KEY_MARKER + it]] }
        val image = Buffer()
        var start = 0
        for (marker in markers) {
            encrypted.copyTo(image, 1L + start, (marker - start).toLong())
            start = marker + 1
        }
        encrypted.copyTo(image, 1L + start, encrypted.size - 1 - start)

        val iv = IvParameterSpec(cpx.copyOfRange(mode.ivOffset, mode.ivOffset + 16))
        val head = image.readByteArray(minOf(mode.encryptedLength, imageLength).toLong())
        // CBC carries PKCS#7 padding, so its plaintext comes out shorter than the encrypted head.
        val plainHead = newAesCipher(mode.transformation, sharedSecret(serverKey, privateKey), iv).doFinal(head)
        return Buffer().write(plainHead).apply { writeAll(image) }
    }

    /** [MARKER_COUNT] distinct positions below [range], ascending, from a 32-bit LCG (Numerical Recipes constants). */
    private fun markerPositions(range: Int, seed: Long): IntArray {
        var state = seed and 0xFFFFFFFF
        val positions = LinkedHashSet<Int>()
        while (positions.size < MARKER_COUNT) {
            state = (state * 1664525 + 1013904223) and 0xFFFFFFFF
            positions += (state.toDouble() / 4294967296.0 * range).toInt()
        }
        return positions.toIntArray().apply { sort() }
    }

    private fun sharedSecret(serverKey: ByteArray, privateKey: PrivateKey): ByteArray = KeyAgreement.getInstance("ECDH").run {
        init(privateKey)
        doPhase(Ece.rawToPublicKey(serverKey), true)
        generateSecret()
    }

    private class Mode(val transformation: String, val ivOffset: Int, val encryptedLength: Int)

    // Keyed by the mode byte: cipher, where the IV sits in cpx and how many leading image bytes are encrypted.
    private val MODES = mapOf(
        3 to Mode("AES/CTR/NoPadding", 25, 25600),
        6 to Mode("AES/CBC/PKCS5Padding", 33, 21520),
        7 to Mode("AES/CTR/NoPadding", 25, 25600),
        8 to Mode("AES/CTR/NoPadding", 31, 25600),
    )

    private const val MARKER_COUNT = 69
    private const val SERVER_KEY_MARKER = 4
    private const val SERVER_KEY_SIZE = 65
}
