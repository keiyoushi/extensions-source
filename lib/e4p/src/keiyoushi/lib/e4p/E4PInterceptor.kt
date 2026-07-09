package keiyoushi.lib.e4p

import keiyoushi.utils.decodeHex
import keiyoushi.utils.readIntLittleEndian
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import java.io.IOException

// drm_worker.js f128 + wasm xebp_render
class E4PInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val fragmentParts = request.url.fragment?.split("\n")
        val qscKey = fragmentParts?.firstOrNull()

        if (qscKey.isNullOrEmpty()) return chain.proceed(request)

        val dirResponse = chain.proceed(
            request.newBuilder()
                .header("Range", "bytes=0-${QscArchive.DIR_SIZE - 1}")
                .build(),
        )
        if (!dirResponse.isSuccessful) return dirResponse
        val dirBytes = dirResponse.body.bytes()

        val entry = QscArchive.findEntry(dirBytes, qscKey)
            ?: throw IOException("QSC file not found: $qscKey")

        val fileStart = QscArchive.DIR_SIZE + entry.offset
        val fileEnd = fileStart + entry.size - 1
        val fileRequest = request.newBuilder()
            .header("Range", "bytes=$fileStart-$fileEnd")
            .build()

        val fileResponse = chain.proceed(fileRequest)
        val xebpBytes = fileResponse.body.bytes()

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

        val body = if (ctx != null) {
            XebpDecoder.decrypt(xebpBytes, ctx)
        } else {
            stripToWebp(xebpBytes)
        }.let { it.asResponseBody(WEBP_MEDIA_TYPE, it.size) }

        return fileResponse.newBuilder()
            .removeHeader("Content-Range")
            .removeHeader("Content-Length")
            .code(200)
            .message("OK")
            .protocol(Protocol.HTTP_1_1)
            .body(body)
            .build()
    }

    private fun stripToWebp(xebp: ByteArray): Buffer {
        val out = Buffer()
        val isRiff = xebp.size >= 20 &&
            xebp[0] == 'R'.code.toByte() && xebp[1] == 'I'.code.toByte() &&
            xebp[2] == 'F'.code.toByte() && xebp[3] == 'F'.code.toByte()
        if (!isRiff) return out.write(xebp)

        val vp8Size = xebp.readIntLittleEndian(16)
        val webpEnd = 20 + vp8Size + (vp8Size and 1)
        if (webpEnd > xebp.size) return out.write(xebp)

        out.writeUtf8("RIFF")
        out.writeIntLe(webpEnd - 8)
        out.writeUtf8("WEBP")
        out.write(xebp, 12, webpEnd - 12)
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
            val size = directory.readIntLittleEndian(base + 4)
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
