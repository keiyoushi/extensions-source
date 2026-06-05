package eu.kanade.tachiyomi.extension.zh.happymh

import android.util.Base64
import okio.Buffer
import okio.InflaterSource
import okio.buffer
import okio.source
import java.security.MessageDigest
import java.util.zip.Inflater

class Decoder {

    // scandec.wasm -> decrypt()
    fun decodeScans(encryptedScans: String): String {
        // key (32) + nonce (16) + b64 cipher
        val buf = encryptedScans.toByteArray()

        // secret + first 8 bytes of encrypted data + domain
        val digest = sha256(SECRET.toByteArray() + buf.copyOfRange(0, 8) + DOMAIN.toByteArray())
        val off1 = (digest[0].toInt() and 0xFF) % 24 + 8
        val off2 = (digest[1].toInt() and 0xFF) % 24 + 8
        val off3 = (digest[2].toInt() and 0xFF) % 24 + 8

        // key, nonce, and ciphertext from offsets
        val str = encryptedScans
        val key = hexToBytes(str.substring(off1 + 8, off1 + 72))
        val nonce = hexToBytes(str.substring(off1 + 72 + off2, off1 + 72 + off2 + 32))
        val ciphertext = Base64.decode(str.substring(off1 + 72 + off2 + 32 + off3), Base64.DEFAULT)

        // CTR: key (32) + nonce (16) + counter (4)
        val state = ByteArray(52)
        key.copyInto(state, 0)
        nonce.copyInto(state, 32)

        val plain = ByteArray(ciphertext.size)
        for (i in ciphertext.indices step 32) {
            val blockIdx = i / 32
            // counter from 48-51 positions
            state[48] = (blockIdx ushr 24).toByte()
            state[49] = (blockIdx ushr 16).toByte()
            state[50] = (blockIdx ushr 8).toByte()
            state[51] = blockIdx.toByte()

            val keystream = sha256(state)
            val blockSize = minOf(32, ciphertext.size - i)

            // 'SC01' + deflate-compressed JSON
            for (j in 0 until blockSize) {
                plain[i + j] = (ciphertext[i + j].toInt() xor keystream[j].toInt()).toByte()
            }
        }

        if (!plain.startsWith("SC01".encodeToByteArray())) error("Decoding scans failed")

        val decompressed = zlibDecompress(plain.copyOfRange(4, plain.size))
        return decompressed.decodeToString()
    }

    private fun sha256(data: ByteArray) = MessageDigest.getInstance("SHA-256").digest(data)

    private fun hexToBytes(hex: String): ByteArray = ByteArray(hex.length / 2).also { bytes ->
        for (i in hex.indices step 2) {
            bytes[i / 2] = hex.substring(i, i + 2).toInt(16).toByte()
        }
    }

    private fun zlibDecompress(data: ByteArray): ByteArray {
        val inflater = Inflater(true)
        val buffer = Buffer().write(data)
        val source = InflaterSource(buffer, inflater).buffer()
        return source.readByteArray()
    }

    companion object {
        private const val SECRET = "DEV_SCAN_SECRET_2026_change_me"
        private const val DOMAIN = "happymh.com"

        private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
            if (size < prefix.size) return false
            for (i in prefix.indices) {
                if (this[i] != prefix[i]) return false
            }
            return true
        }
    }
}
