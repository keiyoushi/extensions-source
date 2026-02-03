package eu.kanade.tachiyomi.extension.en.kagane.wv

import android.util.Base64
import java.security.SecureRandom
import java.security.Signature
import java.security.interfaces.RSAPrivateKey
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PSSParameterSpec

fun String.decodeBase64(): ByteArray = Base64.decode(this, Base64.DEFAULT)

fun generateRandomBytes(size: Int): ByteArray {
    val bytes = ByteArray(size)
    SecureRandom().nextBytes(bytes)
    return bytes
}

fun ByteArray.toHexString(): String = joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }

@Suppress("SpellCheckingInspection")
fun sign(message: ByteArray, privateKey: RSAPrivateKey): ByteArray {
    val pssParams = PSSParameterSpec("SHA-1", "MGF1", MGF1ParameterSpec.SHA1, 20, 1)

    return Signature.getInstance("SHA1withRSAandMGF1").apply {
        setParameter(pssParams)
        initSign(privateKey)
        update(message)
    }.sign()
}
