package eu.kanade.tachiyomi.extension.zh.bilibilimanga

import keiyoushi.utils.aesCbc
import keiyoushi.utils.aesDecrypt

/**
 * The `bytesData` field signed methods answer with: their JSON, AES-192-CBC encrypted.
 * The key comes from the buvid3 cookie and the request's id field (`comic_id`, `ep_id`, `urls`); the IV is the key's first 16 bytes.
 */
object BytesData {

    fun decrypt(data: ByteArray, buvid3: String, idField: String): String {
        val key = key(buvid3, idField)
        return data.aesDecrypt(aesCbc(key, key.copyOf(16))).decodeToString()
    }

    // Every other byte of buvid3 (up to 18), the platform, the last 3 bytes of the id field, padded to 24 bytes with '='.
    private fun key(buvid3: String, idField: String): ByteArray {
        val buvid = buvid3.encodeToByteArray().filterIndexed { i, _ -> i % 2 == 0 }.take(18).toByteArray()
        val id = idField.encodeToByteArray().takeLast(3).toByteArray()
        val key = buvid + "web".encodeToByteArray() + id
        return key + ByteArray(24 - key.size) { '='.code.toByte() }
    }
}
