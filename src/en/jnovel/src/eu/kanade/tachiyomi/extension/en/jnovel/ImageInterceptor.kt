package eu.kanade.tachiyomi.extension.en.jnovel

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException

class ImageInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val url = request.url
        val qscKey = url.fragment

        if (qscKey.isNullOrEmpty() || !response.isSuccessful) {
            return response
        }

        response.close()

        val dirRequest = request.newBuilder()
            .url(url)
            .header("Range", "bytes=0-${QscArchive.DIR_SIZE - 1}")
            .build()

        val dirResponse = chain.proceed(dirRequest)
        val dirBytes = dirResponse.body.bytes().also { dirResponse.close() }

        val entry = QscArchive.findEntry(dirBytes, qscKey)
            ?: throw IOException("QSC file not found: $qscKey")

        val fileStart = QscArchive.DIR_SIZE + entry.offset
        val fileEnd = fileStart + entry.size - 1
        val fileRequest = request.newBuilder()
            .url(url)
            .header("Range", "bytes=$fileStart-$fileEnd")
            .build()

        val fileResponse = chain.proceed(fileRequest)
        val xebpBytes = fileResponse.body.bytes().also { fileResponse.close() }

        return fileResponse.newBuilder()
            .removeHeader("Content-Range")
            .removeHeader("Content-Length")
            .code(200)
            .message("OK")
            .protocol(Protocol.HTTP_1_1)
            .body(xebpBytes.toResponseBody(MEDIA_TYPE))
            .build()
    }

    companion object {
        private val MEDIA_TYPE = "image/webp".toMediaType()
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
            if (directory[i] != MAGIC[i]) throw Exception("Invalid QSC magic at offset $i")
        }

        var runningOffset = 0
        for (i in 0 until ENTRY_COUNT) {
            val base = 32 + i * ENTRY_SIZE
            val size = ((directory[base + 4].toInt() and 0xFF)) or
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
