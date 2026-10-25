package eu.kanade.tachiyomi.extension.ar.mangatek

import android.util.Base64
import keiyoushi.utils.aesDecrypt
import keiyoushi.utils.aesGcm
import keiyoushi.utils.decodeHex
import keiyoushi.utils.readUShortBigEndian
import keiyoushi.utils.sha256
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

// The site reader's overlay unlock (proof, esid, pow, overlay key unwrap); its WASM module is
// plain SHA-256 over these inputs and AES-GCM is done in JS.
object OverlayCrypto {

    private const val KEY = "ff453871399fe268588a0936b45376022d85ed0fd1292001d5102f6a30291dc1"

    private const val PROOF_SALT = "cc9544a33ccbc953b9e7546c9b134524f3c3a06e81d392f0"
    private const val READER_SEED = "2435eb747e78f582e781909ebbfc14826e4f3954b4268850"

    fun proof(token: String, chapterId: Long): String = "$PROOF_SALT|$token|$chapterId".sha256().toHexString()

    val esid: String = "esid|$READER_SEED".sha256().toHexString().take(8)

    // Smallest base-36 counter whose SHA-256("<nonce>:<counter>") starts with `bits` zero bits; the
    // reader gives up after 5M tries
    suspend fun pow(nonce: String, bits: Int): String? {
        if (bits <= 0) return null
        for (counter in 0L until 5_000_000L) {
            if (counter % 10_000 == 0L) currentCoroutineContext().ensureActive()
            val answer = counter.toString(36)
            if (leadingZeroBits("$nonce:$answer".sha256()) >= bits) return answer
        }
        return null
    }

    fun decryptOverlay(overlay: String, ks: String?, key: String?, token: String, chapterId: Long): String? = when {
        ks != null -> decryptEnvelope(overlay, ks, token, chapterId)
        key != null -> decryptHexBlob(overlay, key)
        else -> null
    }

    // "ivHex:ctHex:tagHex", AES-GCM
    fun decryptHexBlob(blob: String, keyHex: String = KEY): String {
        val (iv, ciphertext, tag) = blob.split(":").also {
            require(it.size == 3) { "Unexpected overlay format" }
        }.map { it.decodeHex() }
        return (ciphertext + tag).aesDecrypt(aesGcm(keyHex.decodeHex(), iv, tagLength = tag.size * 8)).decodeToString()
    }

    // Version byte, then [tagLen u16][ivLen u16][tag][ct][iv] XORed with a SHA-256 keystream
    private fun decryptEnvelope(overlay: String, ks: String, token: String, chapterId: Long): String {
        val bytes = Base64.decode(overlay, Base64.URL_SAFE)
        val version = bytes[0].toInt() and 0xFF
        require(version == 3) { "Unsupported overlay version $version" }

        val data = bytes.copyOfRange(1, bytes.size)
        for (start in data.indices step 32) {
            val block = "$READER_SEED|v$version|$ks|${start / 32}".sha256()
            for (i in start until minOf(start + 32, data.size)) {
                data[i] = (data[i].toInt() xor block[i - start].toInt()).toByte()
            }
        }
        val tagLength = data.readUShortBigEndian(0)
        val ivLength = data.readUShortBigEndian(2)
        val tag = data.copyOfRange(4, 4 + tagLength)
        val ciphertext = data.copyOfRange(4 + tagLength, data.size - ivLength)
        val iv = data.copyOfRange(data.size - ivLength, data.size)

        val key = "$READER_SEED|v$version|$ks|$token|$chapterId".sha256()
        return (ciphertext + tag).aesDecrypt(aesGcm(key, iv, tagLength = tagLength * 8)).decodeToString()
    }

    private fun leadingZeroBits(bytes: ByteArray): Int {
        val index = bytes.indexOfFirst { it.toInt() != 0 }
        if (index == -1) return bytes.size * 8
        return index * 8 + Integer.numberOfLeadingZeros(bytes[index].toInt() and 0xFF) - 24
    }
}
