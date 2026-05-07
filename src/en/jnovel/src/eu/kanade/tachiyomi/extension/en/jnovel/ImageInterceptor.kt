package eu.kanade.tachiyomi.extension.en.jnovel

import keiyoushi.utils.decodeHex
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

// drm_worker.js f128 + wasm xebp_render
class ImageInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val url = request.url
        val fragmentParts = url.fragment?.split("\n")
        val qscKey = fragmentParts?.firstOrNull()

        if (qscKey.isNullOrEmpty() || !response.isSuccessful) {
            return response
        }

        response.close()

        val dirRequest = request.newBuilder()
            .header("Range", "bytes=0-${QscArchive.DIR_SIZE - 1}")
            .build()

        val dirResponse = chain.proceed(dirRequest)
        val dirBytes = dirResponse.body.bytes().also { dirResponse.close() }

        val entry = QscArchive.findEntry(dirBytes, qscKey)
            ?: throw IOException("QSC file not found: $qscKey")

        val fileStart = QscArchive.DIR_SIZE + entry.offset
        val fileEnd = fileStart + entry.size - 1
        val fileRequest = request.newBuilder()
            .header("Range", "bytes=$fileStart-$fileEnd")
            .build()

        val fileResponse = chain.proceed(fileRequest)
        val xebpBytes = fileResponse.body.bytes().also { fileResponse.close() }

        val ctx = if (fragmentParts.size == 5) {
            XebpContext(
                iv = fragmentParts[1].decodeHex(),
                contentId = fragmentParts[2],
                consumerId = fragmentParts[3].decodeHex(),
                pbexSeed = fragmentParts[4].decodeHex(),
            )
        } else {
            null
        }
        val (finalBytes, mediaType) = if (ctx != null) {
            XebpDecoder.decrypt(xebpBytes, ctx) to WEBP_MEDIA_TYPE
        } else {
            stripToWebp(xebpBytes) to WEBP_MEDIA_TYPE
        }

        val body = Buffer().apply { write(finalBytes) }.asResponseBody(mediaType, finalBytes.size.toLong())

        return fileResponse.newBuilder()
            .removeHeader("Content-Range")
            .removeHeader("Content-Length")
            .code(200)
            .message("OK")
            .protocol(Protocol.HTTP_1_1)
            .body(body)
            .build()
    }

    private fun stripToWebp(xebp: ByteArray): ByteArray {
        if (xebp.size < 20 ||
            xebp[0] != 'R'.code.toByte() || xebp[1] != 'I'.code.toByte() ||
            xebp[2] != 'F'.code.toByte() || xebp[3] != 'F'.code.toByte()
        ) {
            return xebp
        }

        val vp8Size = ByteBuffer.wrap(xebp, 16, 4)
            .order(ByteOrder.LITTLE_ENDIAN).int
        val webpEnd = 20 + vp8Size + (vp8Size and 1)
        if (webpEnd > xebp.size) return xebp

        val out = xebp.copyOf(webpEnd)
        val newRiffSize = webpEnd - 8
        out[4] = (newRiffSize and 0xFF).toByte()
        out[5] = ((newRiffSize ushr 8) and 0xFF).toByte()
        out[6] = ((newRiffSize ushr 16) and 0xFF).toByte()
        out[7] = ((newRiffSize ushr 24) and 0xFF).toByte()
        out[8] = 'W'.code.toByte()
        out[9] = 'E'.code.toByte()
        out[10] = 'B'.code.toByte()
        out[11] = 'P'.code.toByte()
        return out
    }

    companion object {
        private val WEBP_MEDIA_TYPE = "image/webp".toMediaType()
    }
}

// in qsc_worker.js
object QscArchive {
    const val DIR_SIZE = 4096
    private const val ENTRY_COUNT = 127
    private const val ENTRY_SIZE = 32
    private const val MAGIC_SIZE = 8

    // Magic: ASCII "E4PQSC" + version "\x01\x00"
    private val MAGIC = byteArrayOf(
        'E'.code.toByte(),
        '4'.code.toByte(),
        'P'.code.toByte(),
        'Q'.code.toByte(),
        'S'.code.toByte(),
        'C'.code.toByte(),
        0x01,
        0x00,
    )

    class Entry(
        val fourCC: String,
        val size: Int,
        val name: String,
        val offset: Int,
    )

    fun findEntry(directory: ByteArray, name: String): Entry? {
        require(directory.size >= DIR_SIZE) {
            "QSC must be at least $DIR_SIZE bytes (${directory.size})"
        }

        for (i in 0 until MAGIC_SIZE) {
            if (directory[i] != MAGIC[i]) throw IOException("Invalid QSC magic at offset $i")
        }

        var runningOffset = 0
        for (i in 0 until ENTRY_COUNT) {
            val base = 32 + i * ENTRY_SIZE
            val size = (directory[base + 4].toInt() and 0xFF) or
                ((directory[base + 5].toInt() and 0xFF) shl 8) or
                ((directory[base + 6].toInt() and 0xFF) shl 16) or
                ((directory[base + 7].toInt() and 0xFF) shl 24)
            if (size == 0) break
            val fourCC = String(directory, base, 4, Charsets.ISO_8859_1)
            val rawName = String(directory, base + 8, 24, Charsets.ISO_8859_1)
            val entryName = rawName.trimEnd('\u0000')
            if (entryName == name) {
                return Entry(fourCC, size, entryName, runningOffset)
            }
            runningOffset += size
        }
        return null
    }
}

class XebpContext(
    val iv: ByteArray,
    val contentId: String,
    val consumerId: ByteArray,
    val pbexSeed: ByteArray,
)
/* {
    init {
        require(iv.size == 32) { "XEBP iv must be 32 bytes, got ${iv.size}" }
        require(consumerId.size == 32) { "consumerId must be 32 bytes, got ${consumerId.size}" }
        require(pbexSeed.size == 48) { "pbexSeed must be 48 bytes, got ${pbexSeed.size}" }
    }
}*/
