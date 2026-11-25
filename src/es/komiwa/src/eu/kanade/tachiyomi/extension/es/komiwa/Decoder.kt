package eu.kanade.tachiyomi.extension.es.komiwa

import android.util.Base64

private const val FNV_OFFSET = 0x811c9dc5.toInt()
private const val FNV_PRIME = 0x1000193

private fun fnv(text: String, start: Int = FNV_OFFSET) = text.fold(start) { hash, char -> (hash xor char.code) * FNV_PRIME }

internal fun fieldName(name: String, salt: String) = "_" + fnv("$name|$salt").toUInt().toString(36).take(4)

internal fun decodeData(data: String, keyPart: String, salt: String): String {
    val key = deriveKey(unmask(keyPart, salt), "work", salt).encodeToByteArray()
    val bytes = Base64.decode(data, Base64.URL_SAFE)
    return ByteArray(bytes.size) { i -> (bytes[i].toInt() xor key[i % key.size].toInt() xor (i and 255)).toByte() }.decodeToString()
}

private fun unmask(part: String, salt: String): String {
    val bytes = Base64.decode(part, Base64.URL_SAFE)
    return ByteArray(bytes.size) { i -> (bytes[i].toInt() xor ((salt[i % salt.length].code xor (31 * i + 131)) and 255)).toByte() }.decodeToString()
}

private fun deriveKey(part: String, purpose: String, salt: String): String {
    val data = listOf(salt, part, purpose).joinToString(Char(1).toString())
    var hash = FNV_OFFSET
    val key = buildString {
        for (i in 0 until 32) {
            if (length >= 43) break
            hash = (fnv(data, hash) xor i) * FNV_PRIME
            append(hash.toUInt().toString(36))
        }
    }
    return (key + "komiwa").take(43)
}
