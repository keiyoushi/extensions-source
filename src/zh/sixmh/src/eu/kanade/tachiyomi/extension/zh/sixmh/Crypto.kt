package eu.kanade.tachiyomi.extension.zh.sixmh

import android.util.Base64
import kotlin.experimental.xor

private val keys = arrayOf("Ni1iWGQ5aU4=", "Ni1SWHlqcnk=", "Ni1vWXZ3Vnk=", "Ni00Wlk1N1U=", "Ni1tYkpwVTc=", "Ni02TU0yRWk=", "Ni01NFRpUXI=", "Ni1QaDV4eDk=", "Ni1iWWdlUFI=", "Ni1aOUEzYlc=")

internal fun decodeData(encodedData: String, cid: Int): String {
    val key = Base64.decode(keys[cid % keys.size], Base64.DEFAULT)
    val keyLength = key.size
    val decodedData = Base64.decode(encodedData, Base64.DEFAULT)
    val decryptedData = StringBuilder()
    for (i in decodedData.indices) {
        val decryptedCharCode = decodedData[i] xor key[i % keyLength]
        decryptedData.appendCodePoint(decryptedCharCode.toInt())
    }
    return Base64.decode(decryptedData.toString(), Base64.DEFAULT).decodeToString()
}
