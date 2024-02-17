package eu.kanade.tachiyomi.lib.speedbinb

private const val URLSAFE_BASE64_LOOKUP = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

internal fun determineKeyPair(src: String?, ptbl: List<String>, ctbl: List<String>): Pair<String, String> {
    val i = mutableListOf(0, 0)

    if (src != null) {
        val filename = src.substringAfterLast("/")

        for (e in filename.indices) {
            i[e % 2] = i[e % 2] + filename[e].code
        }

        i[0] = i[0] % 8
        i[1] = i[1] % 8
    }

    return Pair(ptbl[i[0]], ctbl[i[1]])
}

internal fun decodeScrambleTable(cid: String, sharedKey: String, table: String): String {
    val r = "$cid:$sharedKey"
    var e = r.toCharArray()
        .map { it.code }
        .reduceIndexed { index, acc, i -> acc + (i shl index % 16) } and 2147483647

    if (e == 0) {
        e = 0x12345678
    }

    return buildString(table.length) {
        for (s in table.indices) {
            e = e ushr 1 xor (1210056708 and -(1 and e))
            append(((table[s].code - 32 + e) % 94 + 32).toChar())
        }
    }
}

internal fun generateSharedKey(cid: String): String {
    val randomChars = randomChars(16)
    val cidRepeatCount = (16 + cid.length - 1) / cid.length
    val unk1 = buildString(cid.length * cidRepeatCount) {
        for (i in 0 until cidRepeatCount) {
            append(cid)
        }
    }
    val unk2 = unk1.substring(0, 16)
    val unk3 = unk1.substring(unk1.length - 16, unk1.length)
    var s = 0
    var h = 0
    var u = 0

    return buildString(randomChars.length * 2) {
        for (i in randomChars.indices) {
            s = s xor randomChars[i].code
            h = h xor unk2[i].code
            u = u xor unk3[i].code

            append(randomChars[i])
            append(URLSAFE_BASE64_LOOKUP[(s + h + u) and 63])
        }
    }
}

private fun randomChars(length: Int) = buildString(length) {
    for (i in 0 until length) {
        append(URLSAFE_BASE64_LOOKUP.random())
    }
}
