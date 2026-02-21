package eu.kanade.tachiyomi.extension.pt.randomscan

import keiyoushi.lib.zipinterceptor.ZipInterceptor
import okhttp3.Request
import okhttp3.Response
import okio.BufferedSource
import okio.buffer
import okio.cipherSource
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class LuraZipInterceptor : ZipInterceptor() {
    private fun decryptFile(encryptedData: BufferedSource, keyBytes: ByteArray): BufferedSource {
        val keyHash = MessageDigest.getInstance("SHA-256").digest(keyBytes)

        val key: SecretKey = SecretKeySpec(keyHash, "AES")

        val counter = encryptedData.readByteArray(8)
        val iv = IvParameterSpec(counter)

        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, iv)

        val decryptedData = encryptedData.cipherSource(cipher).buffer()

        return decryptedData
    }

    override fun requestIsZipImage(request: Request): Boolean = request.url.pathSegments.contains("cap-download")

    override fun zipGetByteStream(request: Request, response: Response): InputStream {
        val keyData = listOf("obra_id", "slug", "cap_id", "cap_slug").joinToString("") {
            request.url.queryParameter(it)!!
        }.toByteArray(StandardCharsets.UTF_8)
        val encryptedData = response.body.source()

        val decryptedData = decryptFile(encryptedData, keyData)
        return decryptedData.inputStream()
    }
}
