package eu.kanade.tachiyomi.extension.zh.picacomic

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

// copy from https://github.com/czp3009/picacomic-api
private const val ALGORITHM = "HmacSHA256"

internal fun hmacSHA256(key: String, data: String) = Mac.getInstance(ALGORITHM).apply {
    init(SecretKeySpec(key.toByteArray(), ALGORITHM))
}.doFinal(data.toByteArray())

@Suppress("SpellCheckingInspection")
private val hexTable = "0123456789abcdef".toCharArray()

internal fun ByteArray.convertToString() = buildString(size * 2) {
    this@convertToString.forEach {
        val value = it.toInt() and 0xff
        append(hexTable[value ushr 4])
        append(hexTable[value and 0x0f])
    }
}
