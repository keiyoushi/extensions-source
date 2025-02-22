package eu.kanade.tachiyomi.multisrc.gmanga

import android.util.Base64
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

fun decrypt(responseData: String): String {
    val enc = responseData.split("|")
    val secretKey = enc[3].sha256().hexStringToByteArray()

    return enc[0].aesDecrypt(secretKey, enc[2])
}

private fun String.hexStringToByteArray(): ByteArray {
    val len = length
    val data = ByteArray(len / 2)
    var i = 0
    while (i < len) {
        data[i / 2] = (
            (Character.digit(this[i], 16) shl 4) +
                Character.digit(this[i + 1], 16)
            ).toByte()
        i += 2
    }
    return data
}

private fun String.sha256(): String {
    return MessageDigest
        .getInstance("SHA-256")
        .digest(toByteArray())
        .fold("") { str, it -> str + "%02x".format(it) }
}

private fun String.aesDecrypt(secretKey: ByteArray, ivString: String): String {
    val c = Cipher.getInstance("AES/CBC/PKCS5Padding")
    val sk = SecretKeySpec(secretKey, "AES")
    val iv = IvParameterSpec(Base64.decode(ivString.toByteArray(Charsets.UTF_8), Base64.DEFAULT))
    c.init(Cipher.DECRYPT_MODE, sk, iv)

    val byteStr = Base64.decode(toByteArray(Charsets.UTF_8), Base64.DEFAULT)
    return String(c.doFinal(byteStr))
}
