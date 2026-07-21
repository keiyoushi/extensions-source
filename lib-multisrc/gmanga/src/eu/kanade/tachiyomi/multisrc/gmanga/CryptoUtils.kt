package eu.kanade.tachiyomi.multisrc.gmanga

import android.util.Base64
import keiyoushi.utils.decodeHex
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

fun decrypt(responseData: String): String {
    val enc = responseData.split("|")
    val secretKey = enc[3].sha256().decodeHex()

    return enc[0].aesDecrypt(secretKey, enc[2])
}

private fun String.sha256(): String = MessageDigest
    .getInstance("SHA-256")
    .digest(toByteArray())
    .fold("") { str, it -> str + "%02x".format(it) }

private fun String.aesDecrypt(secretKey: ByteArray, ivString: String): String {
    val c = Cipher.getInstance("AES/CBC/PKCS5Padding")
    val sk = SecretKeySpec(secretKey, "AES")
    val iv = IvParameterSpec(Base64.decode(ivString.toByteArray(Charsets.UTF_8), Base64.DEFAULT))
    c.init(Cipher.DECRYPT_MODE, sk, iv)

    val byteStr = Base64.decode(toByteArray(Charsets.UTF_8), Base64.DEFAULT)
    return String(c.doFinal(byteStr))
}
