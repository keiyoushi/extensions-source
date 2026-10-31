package eu.kanade.tachiyomi.extension.es.capibaratraductor

import android.util.Base64

// Page url decoding from the site's callApi script
fun decodePageUrl(token: String, rv: String, index: Int): String {
    val rvBytes = Base64.decode(rv, Base64.URL_SAFE)
    val seed = RV_MASK.indices.fold(0) { acc, i ->
        (acc shl 8) or ((rvBytes[i * 2].toInt() and 0xFF) xor RV_MASK[i])
    }
    val bytes = Base64.decode(token, Base64.URL_SAFE)
    val random = Mulberry32(seed xor ((index + 1) * PAGE_MULTIPLIER) xor URL_FIELD_KEY)

    var checksum = 90
    val url = ByteArray(bytes.size - 2) { i ->
        val byte = (bytes[i + 2].toInt() xor random.nextByte()) and 0xFF
        checksum = (checksum + byte * (i + 1)) and 0xFF
        byte.toByte()
    }
    check((bytes[1].toInt() xor random.nextByte()) and 0xFF == checksum) { "Invalid page url" }

    return url.decodeToString()
}

private class Mulberry32(private var state: Int) {
    fun nextByte(): Int {
        state += 0x6D2B79F5
        var t = state
        t = (t xor (t ushr 15)) * (t or 1)
        t = t xor (t + (t xor (t ushr 7)) * (t or 61))
        return (t xor (t ushr 14)) ushr 24
    }
}

private val RV_MASK = intArrayOf(60, 110, 243, 114)
private const val PAGE_MULTIPLIER = 2654435761L.toInt()
private const val URL_FIELD_KEY = 2135587861
