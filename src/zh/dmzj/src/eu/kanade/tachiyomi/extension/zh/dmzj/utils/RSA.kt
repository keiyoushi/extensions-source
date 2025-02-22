package eu.kanade.tachiyomi.extension.zh.dmzj.utils

import android.util.Base64
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import javax.crypto.Cipher
import kotlin.math.min

object RSA {
    private val cipher by lazy(LazyThreadSafetyMode.NONE) {
        Cipher.getInstance("RSA/ECB/PKCS1Padding")
    }

    private const val MAX_DECRYPT_BLOCK = 128

    fun getPrivateKey(privateKey: String): PrivateKey {
        val keyBytes = Base64.decode(privateKey, Base64.DEFAULT)
        val pkcs8KeySpec = PKCS8EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance("RSA")
        val privateK = keyFactory.generatePrivate(pkcs8KeySpec)
        return privateK
    }

    @Synchronized // because Cipher is not thread-safe
    fun decrypt(encrypted: String, key: PrivateKey): ByteArray {
        val cipher = this.cipher
        cipher.init(Cipher.DECRYPT_MODE, key) // always reset in case of illegal state
        val encryptedData = Base64.decode(encrypted, Base64.DEFAULT)
        val inputLen = encryptedData.size

        val result = ByteArray(inputLen)
        var resultSize = 0

        for (offset in 0 until inputLen step MAX_DECRYPT_BLOCK) {
            val blockLen = min(MAX_DECRYPT_BLOCK, inputLen - offset)
            resultSize += cipher.doFinal(encryptedData, offset, blockLen, result, resultSize)
        }

        return result.copyOf(resultSize)
    }
}
