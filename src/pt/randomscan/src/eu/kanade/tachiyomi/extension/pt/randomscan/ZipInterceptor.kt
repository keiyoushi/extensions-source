package eu.kanade.tachiyomi.extension.pt.randomscan

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.util.Base64
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.buffer
import okio.cipherSource
import tachiyomi.decoder.ImageDecoder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import ca.mpreg.imagedecoder.ImageDecoder as MpregImageDecoder

internal fun Response.decrypt(key: String, prefix: Long = 16): Response {
    val source = body.source()
    val keyHash = MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
    val iv = IvParameterSpec(source.readByteArray(prefix))

    val cipher = Cipher.getInstance("AES/CTR/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyHash, "AES"), iv)

    val decryptedBody = source.cipherSource(cipher).buffer().asResponseBody(body.contentType())
    return newBuilder().body(decryptedBody).build()
}

class ZipInterceptor {

    private fun requestIsZipImage(request: Request) = request.url.pathSegments.contains("download")

    private fun zipGetByteStream(request: Request, response: Response): InputStream {
        val key = SALT + request.url.fragment!!.substringAfter("|")
        return response.decrypt(key).body.byteStream()
    }

    fun zipImageInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        if (!requestIsZipImage(request)) return response

        return response.use { response ->
            if (!response.isSuccessful) {
                throw IOException("Falha na resposta ao descompactar: ${response.code}")
            }

            val filename = request.url.pathSegments.last(String::isNotEmpty)

            val images = ZipInputStream(zipGetByteStream(request, response)).use { zis ->
                generateSequence { zis.nextEntry }
                    .mapNotNull {
                        val entryName = it.name
                        val splitEntryName = entryName.split('.')
                        val entryIndex = splitEntryName.first().toIntOrNull()
                            ?: return@mapNotNull null
                        val entryType = splitEntryName.last()

                        val imageData = if (entryType == "avif" || splitEntryName.size == 1) {
                            zis.readBytes()
                        } else {
                            val svgBytes = zis.readBytes()
                            val svgContent = svgBytes.toString(Charsets.UTF_8)
                            val b64 = dataUriRegex.find(svgContent)?.groupValues?.get(1)
                                ?: return@mapNotNull null

                            Base64.decode(b64, Base64.DEFAULT)
                        }

                        entryIndex to decodeImage(imageData, filename, entryName)
                    }
                    .sortedBy { it.first }
                    .toList()
            }

            if (images.isEmpty()) throw IOException("Nenhuma imagem encontrada no ZIP")

            val totalWidth = images.maxOf { it.second.width }
            val totalHeight = images.sumOf { it.second.height }

            val result = Bitmap.createBitmap(totalWidth, totalHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)

            var dy = 0

            images.forEach {
                val srcRect = Rect(0, 0, it.second.width, it.second.height)
                val dstRect = Rect(0, dy, it.second.width, dy + it.second.height)

                canvas.drawBitmap(it.second, srcRect, dstRect, null)

                dy += it.second.height
            }

            val output = ByteArrayOutputStream()
            result.compress(Bitmap.CompressFormat.JPEG, 90, output)

            val image = output.toByteArray()
            val body = image.toResponseBody("image/jpeg".toMediaType())

            response.newBuilder()
                .header("Content-Type", "image/jpeg")
                .body(body)
                .build()
        }
    }

    private fun decodeImage(data: ByteArray, filename: String, entryName: String): Bitmap {
        val bitmap = runCatching {
            ImageDecoder.newInstance(ByteArrayInputStream(data), false, null)?.run {
                try {
                    decode(Rect(0, 0, width, height), 1)
                } finally {
                    recycle()
                }
            }
        }.getOrNull() ?: runCatching {
            MpregImageDecoder.open(ByteArrayInputStream(data)).use { decoder ->
                decoder.decodeNext().use { frame ->
                    Bitmap.createBitmap(frame.width, frame.height, Bitmap.Config.ARGB_8888)
                        .apply { copyPixelsFromBuffer(frame.image) }
                }
            }
        }.getOrNull()

        return bitmap ?: throw IOException("Não foi possível decodificar a imagem $filename#$entryName")
    }

    companion object {
        private const val SALT = "6514b4a8"
        private val dataUriRegex = Regex("""base64,([0-9a-zA-Z/+=\s]+)""")
    }
}
