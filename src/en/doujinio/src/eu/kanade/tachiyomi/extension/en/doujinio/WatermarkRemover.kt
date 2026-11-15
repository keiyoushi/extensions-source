package eu.kanade.tachiyomi.extension.en.doujinio

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import keiyoushi.utils.parseAs
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class WatermarkRemover : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val keys = request.url.fragment?.parseAs<MangaKeys>() ?: return chain.proceed(request)
        val response = chain.proceed(request)

        val cleanedImage = replaceJpeg(response.body.bytes(), keys.toByteArray())

        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(cleanedImage.toResponseBody(response.body.contentType()))
            .build()
    }
}

// drmwasm_bg-*.wasm: Replace original data packed in image data with watermark area
fun replaceJpeg(image: ByteArray, key: ByteArray): ByteArray {
    val ciphertext = extractCiphertext(image)
    val plaintext = decrypt(ciphertext, key)

    // plaintext buffer: u32_be(size) | JPEG | u32_le(x, y, width, height)
    val buffer = ByteBuffer.wrap(plaintext).order(ByteOrder.BIG_ENDIAN)
    val totalSize = buffer.int
    val jpegStart = 4 // skip size
    val jpegEnd = jpegStart + totalSize - 16
    val jpegLength = jpegEnd - jpegStart

    buffer.order(ByteOrder.LITTLE_ENDIAN)
    buffer.position(jpegEnd)
    val x = buffer.int
    val y = buffer.int
    val width = buffer.int
    val height = buffer.int

    val overlay = BitmapFactory.decodeByteArray(plaintext, jpegStart, jpegLength)
        ?: error("Failed to decode overlay JPEG")
    val base = BitmapFactory.decodeByteArray(image, 0, image.size)
        ?: error("Failed to decode base image")

    val result = Bitmap.createBitmap(base.width, base.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)
    canvas.drawBitmap(base, 0f, 0f, null)
    canvas.drawBitmap(overlay, x.toFloat(), y.toFloat(), null)

    val stream = ByteArrayOutputStream()
    result.compress(Bitmap.CompressFormat.JPEG, 90, stream)

    base.recycle()
    overlay.recycle()
    result.recycle()

    return stream.toByteArray()
}

// Extract the cipher section in a 0xFFEA marker
private fun extractCiphertext(data: ByteArray): ByteArray {
    var offset = 2

    while (offset < data.size) {
        if (data[offset].toInt() and 0xFF != 0xFF) {
            offset++
            continue
        }
        val marker = data[offset + 1].toInt() and 0xFF
        offset += 2
        if (marker == 0xD9) break // EOI
        if (marker == 0xDA) break // SOS
        if (marker == 0xD8 || marker in 0xD0..0xD7) continue

        val length = ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF) // u16 big-endian, includes itself
        val payloadStart = offset + 2
        val payloadEnd = offset + length

        val payload = data.sliceArray(payloadStart until payloadEnd)

        if (payload.size >= 4 && payload[0] == 0x4D.toByte() && payload[1] == 0x49.toByte() &&
            payload[2] == 0x4C.toByte() && payload[3] == 0x46.toByte() // "MILF"
        ) {
            // skip tag (4 bytes) + extra 3 bytes
            return payload.sliceArray(7 until payload.size)
        }
        offset = payloadEnd
    }
    error("Failed to find encrypted segment")
}

private fun decrypt(ciphertext: ByteArray, key: ByteArray): ByteArray {
    val cipher = Cipher.getInstance("AES/ECB/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"))
    val decryptedBytes = cipher.doFinal(ciphertext)
    return decryptedBytes
}
