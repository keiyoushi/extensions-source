package eu.kanade.tachiyomi.extension.pt.randomscan

import eu.kanade.tachiyomi.lib.zipinterceptor.ZipInterceptor
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class LuraZipInterceptor : ZipInterceptor() {
    fun decryptFile(encryptedData: ByteArray, keyBytes: ByteArray): ByteArray {
        val keyHash = MessageDigest.getInstance("SHA-256").digest(keyBytes)

        val key: SecretKey = SecretKeySpec(keyHash, "AES")

        val counter = encryptedData.copyOfRange(0, 8)
        val iv = IvParameterSpec(counter)

        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, iv)

        val decryptedData = cipher.doFinal(encryptedData.copyOfRange(8, encryptedData.size))

        return decryptedData
    }

    override fun requestIsZipImage(request: Request): Boolean {
        return request.url.pathSegments.contains("cap-download")
    }

    override fun zipGetByteStream(request: Request, response: Response): InputStream {
        val keyData = listOf("obra_id", "slug", "cap_id", "cap_slug").joinToString("") {
            request.url.queryParameterValues(it).first().toString()
        }.toByteArray(StandardCharsets.UTF_8)
        val encryptedData = response.body.bytes()

        val decryptedData = decryptFile(encryptedData, keyData)
        return ByteArrayInputStream(decryptedData)
    }
}
