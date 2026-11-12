package eu.kanade.tachiyomi.extension.es.catharsisworld

// The reader's page shuffle generator, keyed by X-Page-Key
internal fun pageShuffle(key: String): () -> Double {
    var r = 0x811c9dc5.toInt()
    var s = 0x12B9B0A5
    for (ch in key) {
        val y = ch.code
        r = (r xor y) * 0x01000193
        s = (s xor ((y shl 5) or (y ushr 27))) * 0x5BD1E995
    }
    val h = (s xor 0x6D2B79F5) or 1
    val stepA = 13 + (r ushr 3).mod(5)
    val stepV = 6 + (s ushr 5).mod(4)
    val stepY = 12 + ((r xor s) ushr 7).mod(5)
    val p = ((s ushr 2) and 14) or 1
    val q = ((r ushr 4) and 126) or 1
    var x = r xor (s ushr 16)
    return {
        x += h
        var m = x
        m = (m xor (m ushr stepA)) * (m or p)
        m = m xor (m + (m xor (m ushr stepV)) * (m or q))
        (m xor (m ushr stepY)).toUInt().toDouble() / 4294967296.0
    }
}
