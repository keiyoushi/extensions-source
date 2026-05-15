package eu.kanade.tachiyomi.extension.ja.mokuro

import keiyoushi.utils.decodeHex
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import okio.buffer
import okio.source
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Collections
import java.util.LinkedHashMap
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream
import kotlin.math.min

class CbzInterceptor : Interceptor {

    private val indexCache =
        Collections.synchronizedMap(
            object : LinkedHashMap<String, Map<String, ZipEntryInfo>>(20, 0.75f, true) {
                override fun removeEldestEntry(
                    eldest: MutableMap.MutableEntry<String, Map<String, ZipEntryInfo>>?,
                ): Boolean = size > 20
            },
        )

    private class ZipEntryInfo(
        val offset: Long,
        val compressedSize: Long,
        val uncompressedSize: Long,
        val compressionMethod: Int,
        val nameLen: Int,
        val extraLen: Int,
    )

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val fragment = request.url.fragment

        if (fragment == null || !request.url.pathSegments.last().endsWith(".cbz", true)) {
            return chain.proceed(request)
        }

        val baseUrl = request.url.newBuilder().fragment(null).build()
        val urlKey = baseUrl.toString()

        val index = indexCache.getOrPut(urlKey) {
            fetchZipIndex(chain, baseUrl)
        }

        val entry = index[fragment]
            ?: throw IOException("Entry $fragment not found in zip")

        // We fetch enough to cover the local file header (30 bytes + name + extra) and the compressed data
        // We add a 512-byte safety buffer to ensure we capture the entire header even if extraField lengths differ
        val rangeEnd = entry.offset + 30 + entry.nameLen + entry.extraLen + entry.compressedSize + 512

        val entryRequest = request.newBuilder()
            .url(baseUrl)
            .header("Range", "bytes=${entry.offset}-$rangeEnd")
            .build()

        val response = chain.proceed(entryRequest)
        if (!response.isSuccessful && response.code != 206) {
            response.close()
            throw IOException("Failed to fetch zip entry: ${response.code}")
        }

        return try {
            val stream = response.body.byteStream()
            val headerBytes = ByteArray(30)
            var read = 0
            while (read < 30) {
                val count = stream.read(headerBytes, read, 30 - read)
                if (count == -1) throw IOException("Truncated Local File Header")
                read += count
            }

            val view = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)
            if (view.getInt(0) != 0x04034b50) {
                throw IOException("Invalid local file header signature")
            }

            val compressionMethod = view.getShort(8).toInt() and 0xFFFF
            val nameLen = view.getShort(26).toInt() and 0xFFFF
            val extraLen = view.getShort(28).toInt() and 0xFFFF

            val skipBytes = (nameLen + extraLen).toLong()
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

            dataStream = TruncatingInputStream(dataStream, entry.uncompressedSize)

            val contentType = getContentType(fragment)

            response.newBuilder()
                .code(200)
                .message("OK")
                .body(dataStream.source().buffer().asResponseBody(contentType.toMediaType()))
                .build()
        } catch (e: Exception) {
            response.close()
            throw if (e is IOException) e else IOException(e)
        }
    }

    private fun fetchZipIndex(
        chain: Interceptor.Chain,
        url: HttpUrl,
    ): Map<String, ZipEntryInfo> {
        val sizeRequest = Request.Builder()
            .url(url)
            .header("Range", "bytes=0-0")
            .build()

        val fileSize = chain.proceed(sizeRequest).use { response ->
            if (!response.isSuccessful && response.code != 206) {
                throw IOException("Failed to get file size: ${response.code}")
            }

            val contentRange = response.header("Content-Range")
                ?: throw IOException("Missing Content-Range")

            contentRange.substringAfterLast("/")
                .toLongOrNull()
                ?: throw IOException("Invalid Content-Range: $contentRange")
        }

        val eocdFetchSize = min(fileSize, 65535L + 22L)

        val eocdRequest = Request.Builder()
            .url(url)
            .header("Range", "bytes=${fileSize - eocdFetchSize}-${fileSize - 1}")
            .build()

        val eocdBytes = chain.proceed(eocdRequest).use { response ->
            if (!response.isSuccessful && response.code != 206) {
                throw IOException("Failed to fetch EOCD")
            }
            response.body.source().readByteString()
        }

        val eocdSignature = "504b0506".decodeHex()
        val eocdIndex = eocdBytes.lastIndexOf(eocdSignature)

        if (eocdIndex < 0 || eocdIndex + 22 > eocdBytes.size) {
            throw IOException("EOCD not found or corrupted")
        }

        val buffer = Buffer().write(eocdBytes)
        buffer.skip(eocdIndex + 10L)

        val totalEntries = buffer.readShortLe().toInt() and 0xFFFF
        val cdSize = buffer.readIntLe().toLong() and 0xFFFFFFFFL
        val cdOffset = buffer.readIntLe().toLong() and 0xFFFFFFFFL

        val cdRequest = Request.Builder()
            .url(url)
            .header("Range", "bytes=$cdOffset-${cdOffset + cdSize - 1}")
            .build()

        return chain.proceed(cdRequest).use { response ->
            if (!response.isSuccessful && response.code != 206) {
                throw IOException("Failed to fetch Central Directory")
            }

            val src = response.body.source()
            val map = HashMap<String, ZipEntryInfo>(totalEntries)

            repeat(totalEntries) {
                if (src.exhausted()) return@repeat
                if (src.readIntLe() != 0x02014b50) return@repeat

                src.skip(6)
                val method = src.readShortLe().toInt() and 0xFFFF
                src.skip(8)
                val compSize = src.readIntLe().toLong() and 0xFFFFFFFFL
                val uncompSize = src.readIntLe().toLong() and 0xFFFFFFFFL
                val nameLen = src.readShortLe().toInt() and 0xFFFF
                val extraLen = src.readShortLe().toInt() and 0xFFFF
                val commLen = src.readShortLe().toInt() and 0xFFFF
                src.skip(8)
                val offset = src.readIntLe().toLong() and 0xFFFFFFFFL
                val name = src.readString(nameLen.toLong(), Charsets.UTF_8)
                src.skip((extraLen + commLen).toLong())

                map[name] = ZipEntryInfo(offset, compSize, uncompSize, method, nameLen, extraLen)
            }
            map
        }
    }

    private fun getContentType(name: String): String {
        val lower = name.lowercase()
        return when {
            lower.endsWith(".webp") -> "image/webp"
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
            lower.endsWith(".png") -> "image/png"
            else -> "application/octet-stream"
        }
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
