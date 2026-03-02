package eu.kanade.tachiyomi.extension.en.kagane.wv

import android.util.Base64
import java.math.BigInteger
import java.security.KeyFactory
import java.security.interfaces.RSAPrivateKey
import java.security.spec.InvalidKeySpecException
import java.security.spec.PKCS8EncodedKeySpec

@Suppress("SpellCheckingInspection")
private fun buildPkcs8KeyFromPkcs1Key(innerKey: ByteArray): ByteArray {
    val result = ByteArray(innerKey.size + 26)
    System.arraycopy(Base64.decode("MIIEvAIBADANBgkqhkiG9w0BAQEFAASCBKY=", Base64.DEFAULT), 0, result, 0, 26)
    System.arraycopy(BigInteger.valueOf((result.size - 4).toLong()).toByteArray(), 0, result, 2, 2)
    System.arraycopy(BigInteger.valueOf(innerKey.size.toLong()).toByteArray(), 0, result, 24, 2)
    System.arraycopy(innerKey, 0, result, 26, innerKey.size)
    return result
}

fun getKey(bytes: ByteArray): RSAPrivateKey = try {
    val keySpec = PKCS8EncodedKeySpec(bytes)
    val keyFactory = KeyFactory.getInstance("RSA")
    keyFactory.generatePrivate(keySpec) as RSAPrivateKey
} catch (_: InvalidKeySpecException) {
    val keySpec = PKCS8EncodedKeySpec(buildPkcs8KeyFromPkcs1Key(bytes))
    val keyFactory = KeyFactory.getInstance("RSA")
    keyFactory.generatePrivate(keySpec) as RSAPrivateKey
}
