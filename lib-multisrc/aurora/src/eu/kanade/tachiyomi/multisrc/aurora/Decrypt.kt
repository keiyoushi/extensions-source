package eu.kanade.tachiyomi.multisrc.aurora

import android.util.Base64
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

fun decrypt(payload: String, key: String): String {
    val bytes = Base64.decode(payload, Base64.URL_SAFE or Base64.NO_WRAP)

    val baseData = bytes.copyOfRange(5, 13)

    val cipherText = bytes.copyOfRange(13, bytes.size)
    val totalLength = cipherText.size

    val chapterKey = Base64.decode(key, Base64.DEFAULT)
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(chapterKey, "HmacSHA256"))

    val keystream = ByteArray(totalLength)
    var o = 0
    var i = 0

    while (o < totalLength) {
        val block = ByteArray(baseData.size + 1)
        System.arraycopy(baseData, 0, block, 0, baseData.size)
        block[baseData.size] = (i and 0xFF).toByte()

        val hashBlock = mac.doFinal(block)

        val copyLength = minOf(hashBlock.size, totalLength - o)
        System.arraycopy(hashBlock, 0, keystream, o, copyLength)

        o += copyLength
        i++
    }

    val decryptedBytes = ByteArray(totalLength)
    for (idx in cipherText.indices) {
        decryptedBytes[idx] = (cipherText[idx].toInt() xor (keystream[idx].toInt() and 0xFF)).toByte()
    }

    return String(decryptedBytes, Charsets.UTF_8)
}

fun getParams(value: String): Pair<Int, Int> {
    val byteOffset = 1
    val byteArray = Base64.decode(value, Base64.URL_SAFE or Base64.NO_WRAP)
    val buffer = ByteBuffer.wrap(byteArray)
    buffer.order(ByteOrder.BIG_ENDIAN)
    return byteArray.first().toInt() to buffer.getInt(byteOffset)
}
