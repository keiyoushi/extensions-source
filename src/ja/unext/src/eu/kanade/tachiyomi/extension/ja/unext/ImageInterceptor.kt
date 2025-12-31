package eu.kanade.tachiyomi.extension.ja.unext

import keiyoushi.utils.parseAs
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import okio.ByteString.Companion.decodeBase64
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class ImageInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url

        if (url.host != "127.0.0.1") {
            return chain.proceed(request)
        }

        val data = url.fragment?.parseAs<ImageRequestData>()
            ?: throw IOException("No API fragment found")

        val zipFile = File(data.zipPath)
        if (!zipFile.exists()) throw IOException("File not found at ${data.zipPath}")

        val buffer = Buffer()

        ZipFile(zipFile).use { zip ->
            val entry = zip.getEntry(data.entryName)
                ?: throw IOException("Entry not found: ${data.entryName}")

            val keyBytes = data.key.decodeBase64()?.toByteArray()
                ?: throw IOException("Invalid Key Base64")
            val ivBytes = data.iv.decodeBase64()?.toByteArray()
                ?: throw IOException("Invalid IV Base64")

            val cipher = Cipher.getInstance("AES/CBC/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), IvParameterSpec(ivBytes))

            zip.getInputStream(entry).use { fileStream ->
                CipherInputStream(fileStream, cipher).use { cipherStream ->
                    buffer.readFrom(cipherStream)
                }
            }
        }

        val finalBuffer = if (data.size != null && buffer.size > data.size) {
            val exactBuffer = Buffer()
            exactBuffer.write(buffer, data.size)
            buffer.clear()
            exactBuffer
        } else if (data.size == null && buffer.size > 0) {
            val lastByte = buffer[buffer.size - 1].toInt()
            if (lastByte in 1..16) {
                val exactSize = buffer.size - lastByte
                val exactBuffer = Buffer()
                exactBuffer.write(buffer, exactSize)
                buffer.clear()
                exactBuffer
            } else {
                buffer
            }
        } else {
            buffer
        }

        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(finalBuffer.asResponseBody("image/webp".toMediaType(), finalBuffer.size))
            .build()
    }
}
