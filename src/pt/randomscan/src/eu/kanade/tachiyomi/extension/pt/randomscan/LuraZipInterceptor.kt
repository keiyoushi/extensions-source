package eu.kanade.tachiyomi.extension.pt.randomscan

import keiyoushi.lib.zipinterceptor.ZipInterceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.buffer
import okio.cipherSource
import java.io.InputStream
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

internal fun Response.decrypt(key: String, prefix: Long = 16): Response {
    val source = body.source()
    val keyHash = MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
    val iv = IvParameterSpec(source.readByteArray(prefix))

    val cipher = Cipher.getInstance("AES/CTR/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyHash, "AES"), iv)

    val decryptedBody = source.cipherSource(cipher).buffer().asResponseBody(body.contentType())
    return newBuilder().body(decryptedBody).build()
}

class LuraZipInterceptor : ZipInterceptor() {
    override fun requestIsZipImage(request: Request) = request.url.pathSegments.contains("download")

    override fun zipGetByteStream(request: Request, response: Response): InputStream {
        val key = SALT + request.url.fragment!!.substringAfter("|")
        return response.decrypt(key).body.byteStream()
    }
}

const val SALT = "6514b4a8"
