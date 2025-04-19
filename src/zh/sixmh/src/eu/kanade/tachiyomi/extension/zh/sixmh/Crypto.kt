package eu.kanade.tachiyomi.extension.zh.sixmh

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

internal fun decodeData(encodedData: String): String {
    val aesKey = "9S8\$vJnU2ANeSRoF"
    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    val secretKeySpec = SecretKeySpec(aesKey.toByteArray(), "AES")

    val decodedBase64 = Base64.decode(encodedData, Base64.DEFAULT)
    val iv = IvParameterSpec(decodedBase64.sliceArray(0 until 16))
    val cipherText = decodedBase64.sliceArray(16 until decodedBase64.size)
    cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, iv)
    val decryptedText = cipher.doFinal(cipherText)

    return String(decryptedText)
}
