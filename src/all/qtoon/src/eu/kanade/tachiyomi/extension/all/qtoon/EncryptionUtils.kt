package eu.kanade.tachiyomi.extension.all.qtoon

import android.util.Base64
import keiyoushi.utils.parseAs
import okhttp3.Response
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

fun generateRandomString(length: Int): String {
    val allowedChars = ('A'..'Z') + ('a'..'z') + ('2'..'8')
    return (1..length)
        .map { allowedChars.random() }
        .joinToString("")
}

private fun md5(input: String): String {
    val md = MessageDigest.getInstance("MD5")
    val digest = md.digest(input.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") {
        "%02x".format(it)
    }
}

private fun aesDecrypt(data: String, key: ByteArray, iv: ByteArray): String {
    val encryptedData = Base64.decode(data, Base64.DEFAULT)

    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding").apply {
        val keySpec = SecretKeySpec(key, "AES")
        val ivSpec = IvParameterSpec(iv)

        init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
    }

    val decryptedData = cipher.doFinal(encryptedData)

    return String(decryptedData, Charsets.UTF_8)
}

fun decrypt(response: Response): String {
    val res = response.parseAs<EncryptedResponse>()
    val requestToken = response.request.header("did")!!

    val inner = md5("$requestToken${res.ts}")
    val outer = md5("${inner}OQlM9JBJgLWsgffb")

    val key = outer.substring(0, 16).toByteArray(Charsets.UTF_8)
    val iv = outer.substring(16, 32).toByteArray(Charsets.UTF_8)

    return aesDecrypt(res.data, key, iv)
}

inline fun <reified T> Response.decryptAs(): T {
    return decrypt(this).parseAs()
}

fun decryptImageUrl(url: String, requestToken: String): String {
    val inner = md5(requestToken)
    val outer = md5("${inner}9tv86uBwmOYs7QZ0")

    val key = outer.substring(0, 16).toByteArray(Charsets.UTF_8)
    val iv = outer.substring(16, 32).toByteArray(Charsets.UTF_8)

    return aesDecrypt(url, key, iv)
}

val mobileUserAgentRegex = Regex(
    """android|avantgo|blackberry|iemobile|ipad|iphone|ipod|j2me|midp|mobile|opera mini|phone|palm|pocket|psp|symbian|up.browser|up.link|wap|windows ce|xda|xiino""",
    RegexOption.IGNORE_CASE,
)
