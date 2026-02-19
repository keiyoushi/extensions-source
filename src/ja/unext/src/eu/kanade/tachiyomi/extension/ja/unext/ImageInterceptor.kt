package eu.kanade.tachiyomi.extension.ja.unext

import keiyoushi.utils.parseAs
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.ByteString.Companion.decodeBase64
import okio.buffer
import okio.source
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream
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
            ?: throw IOException("No fragment found")

        val rangeStart = data.zipStartOffset + data.localFileHeaderOffset
        val rangeEnd = rangeStart + 512 + data.compressedSize

        val newRequest = request.newBuilder()
            .url(data.zipUrl)
            .header("Range", "bytes=$rangeStart-$rangeEnd")
            .build()

        val response = chain.proceed(newRequest)
        if (!response.isSuccessful) {
            response.close()
            throw IOException("Failed to fetch zip entry")
        }

        val stream = response.body.byteStream()

        val headerBytes = ByteArray(30)
        var read = 0
        while (read < 30) {
            val count = stream.read(headerBytes, read, 30 - read)
            if (count == -1) throw IOException("Truncated Local File Header")
            read += count
        }

        val view = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)
        val signature = view.getInt(0)
        // PK\03\04
        if (signature != 0x04034b50) throw IOException("Invalid Local File Header signature")

        val compressionMethod = view.getShort(8).toInt()
        val filenameLen = view.getShort(26).toInt() and 0xFFFF
        val extraLen = view.getShort(28).toInt() and 0xFFFF

        val skipBytes = (filenameLen + extraLen).toLong()
        var skipped = 0L
        while (skipped < skipBytes) {
            val s = stream.skip(skipBytes - skipped)
            if (s <= 0) break
            skipped += s
        }

        var dataStream: InputStream = stream

        if (compressionMethod == 8) {
            dataStream = InflaterInputStream(dataStream, Inflater(true))
        }

        val keyBytes = data.key.decodeBase64()?.toByteArray()
            ?: throw IOException("Invalid Key Base64")
        val ivBytes = data.iv.decodeBase64()?.toByteArray()
            ?: throw IOException("Invalid IV Base64")

        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), IvParameterSpec(ivBytes))

        dataStream = CipherInputStream(dataStream, cipher)
        dataStream = TruncatingInputStream(dataStream, data.originalFileSize)

        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(dataStream.source().buffer().asResponseBody("image/webp".toMediaType()))
            .build()
    }
    private class TruncatingInputStream(inputStream: InputStream, private val limit: Long) : FilterInputStream(inputStream) {
        private var bytesRead = 0L

        override fun read(): Int {
            if (bytesRead >= limit) return -1
            val result = super.read()
            if (result != -1) bytesRead++
            return result
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (bytesRead >= limit) return -1
            val maxRead = minOf(len.toLong(), limit - bytesRead).toInt()
            val num = super.read(b, off, maxRead)
            if (num != -1) bytesRead += num
            return num
        }

        override fun available(): Int = minOf(super.available().toLong(), limit - bytesRead).toInt()
    }
}
