package eu.kanade.tachiyomi.multisrc.aurora

import android.util.Base64
import keiyoushi.utils.hmacSha256
import keiyoushi.utils.readIntBigEndian
import keiyoushi.utils.xorInPlace

fun decrypt(payload: String, key: String): String {
    val bytes = Base64.decode(payload, Base64.URL_SAFE or Base64.NO_WRAP)
    val nonce = bytes.copyOfRange(5, 13)
    val cipherText = bytes.copyOfRange(13, bytes.size)
    val chapterKey = Base64.decode(key, Base64.DEFAULT)

    val keystream = (0..cipherText.size / 32).fold(ByteArray(0)) { stream, block ->
        stream + (nonce + block.toByte()).hmacSha256(chapterKey)
    }
    return cipherText.xorInPlace(keystream).decodeToString()
}

fun getParams(value: String): Pair<Int, Int> {
    val bytes = Base64.decode(value, Base64.URL_SAFE or Base64.NO_WRAP)
    return bytes.first().toInt() to bytes.readIntBigEndian(1)
}
