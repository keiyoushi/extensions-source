package eu.kanade.tachiyomi.multisrc.mmlook

import android.util.Base64
import kotlin.experimental.xor

// all2.js?v=2.3
fun decrypt(data: String, index: Int): String {
    val key = when (index) {
        0 -> "smkhy258"
        1 -> "smkd95fv"
        2 -> "md496952"
        3 -> "cdcsdwq"
        4 -> "vbfsa256"
        5 -> "cawf151c"
        6 -> "cd56cvda"
        7 -> "8kihnt9"
        8 -> "dso15tlo"
        9 -> "5ko6plhy"
        else -> throw Exception("Unknown index: $index")
    }.encodeToByteArray()
    val keyLength = key.size
    val bytes = Base64.decode(data, Base64.DEFAULT)
    for (i in bytes.indices) {
        bytes[i] = bytes[i] xor key[i % keyLength]
    }
    return String(Base64.decode(bytes, Base64.DEFAULT))
}
