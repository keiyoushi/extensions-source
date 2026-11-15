package keiyoushi.lib.speedbinb

private const val URLSAFE_BASE64_LOOKUP = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

internal fun determineKeyPair(src: String, ptbl: List<String>, ctbl: List<String>): Pair<String, String> {
    val sums = IntArray(2)
    src.substringAfterLast("/").forEachIndexed { i, c -> sums[i % 2] += c.code }
    return ptbl[sums[0] % 8] to ctbl[sums[1] % 8]
}

internal fun decodeScrambleTable(cid: String, sharedKey: String, table: String): String {
    var e = "$cid:$sharedKey".foldIndexed(0) { i, acc, c -> acc + (c.code shl i % 16) } and Int.MAX_VALUE

    if (e == 0) {
        e = 0x12345678
    }

    return buildString(table.length) {
        for (c in table) {
            e = e ushr 1 xor (1210056708 and -(e and 1))
            append(((c.code - 32 + e % 94) % 94 + 32).toChar())
        }
    }
}

internal fun generateSharedKey(cid: String): String {
    val repeatedCid = cid.repeat((cid.length + 15) / cid.length)
    val head = repeatedCid.take(16)
    val tail = repeatedCid.takeLast(16)
    var s = 0
    var h = 0
    var u = 0

    return buildString(32) {
        for (i in 0 until 16) {
            val c = URLSAFE_BASE64_LOOKUP.random()
            s = s xor c.code
            h = h xor head[i].code
            u = u xor tail[i].code

            append(c)
            append(URLSAFE_BASE64_LOOKUP[(s + h + u) and 63])
        }
    }
}
