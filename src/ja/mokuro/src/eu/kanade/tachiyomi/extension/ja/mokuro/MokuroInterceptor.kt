package eu.kanade.tachiyomi.extension.ja.mokuro

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.ByteString.Companion.decodeHex
import java.io.IOException
import java.util.Collections
import java.util.LinkedHashMap
import java.util.zip.Inflater
import kotlin.math.min

class MokuroInterceptor : Interceptor {

    private val indexCache =
        Collections.synchronizedMap(
            object : LinkedHashMap<String, Map<String, ZipEntryInfo>>(20, 0.75f, true) {
                override fun removeEldestEntry(
                    eldest: MutableMap.MutableEntry<String, Map<String, ZipEntryInfo>>?,
                ): Boolean = size > 20
            },
        )

    private val inflaterLocal = object : ThreadLocal<Inflater>() {
        override fun initialValue(): Inflater = Inflater(true)
    }

    data class ZipEntryInfo(
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

        val rangeEnd =
            entry.offset + 30 + entry.nameLen + entry.extraLen + entry.compressedSize + 512

        val entryRequest =
            request.newBuilder()
                .url(baseUrl)
                .header("Range", "bytes=${entry.offset}-$rangeEnd")
                .build()

        val response = chain.proceed(entryRequest)

        if (!response.isSuccessful && response.code != 206) {
            response.close()
            throw IOException("Failed to fetch zip entry: ${response.code}")
        }

        return try {
            val source = response.body.source()

            if (source.readIntLe() != 0x04034b50) {
                throw IOException("Invalid local file header signature")
            }

            source.skip(22)

            val nameLen = source.readShortLe().toInt() and 0xFFFF
            val extraLen = source.readShortLe().toInt() and 0xFFFF

            source.skip((nameLen + extraLen).toLong())

            val size = entry.uncompressedSize

            if (size <= 0 || size > MAX_UNCOMPRESSED_SIZE) {
                throw IOException("Invalid uncompressed size: $size")
            }

            val data =
                when (entry.compressionMethod) {
                    0 -> source.readByteArray(size)

                    8 -> {
                        val compressed = source.readByteArray(entry.compressedSize)

                        val inflater = inflaterLocal.get()!!
                        inflater.reset()
                        inflater.setInput(compressed)

                        val output = ByteArray(size.toInt())

                        var offset = 0
                        var lastOffset = -1

                        while (!inflater.finished() && offset < output.size) {
                            val count =
                                inflater.inflate(output, offset, output.size - offset)

                            if (count <= 0) {
                                if (inflater.finished()) break
                                if (offset == lastOffset) {
                                    throw IOException("Inflater stalled")
                                }
                            }

                            lastOffset = offset
                            offset += count
                        }

                        output
                    }

                    else ->
                        throw IOException(
                            "Unsupported compression method: ${entry.compressionMethod}",
                        )
                }

            val contentType = getContentType(fragment)

            response.newBuilder()
                .code(200)
                .body(data.toResponseBody(contentType.toMediaType()))
                .build()
        } catch (e: Exception) {
            if (e is IOException) throw e
            throw IOException(e)
        } finally {
            response.close()
        }
    }

    private fun fetchZipIndex(
        chain: Interceptor.Chain,
        url: HttpUrl,
    ): Map<String, ZipEntryInfo> {
        val sizeRequest =
            Request.Builder()
                .url(url)
                .header("Range", "bytes=0-0")
                .build()

        val fileSize =
            chain.proceed(sizeRequest).use { response ->

                if (!response.isSuccessful && response.code != 206) {
                    throw IOException("Failed to get file size: ${response.code}")
                }

                val contentRange =
                    response.header("Content-Range")
                        ?: throw IOException("Missing Content-Range")

                contentRange.substringAfterLast("/")
                    .toLongOrNull()
                    ?: throw IOException("Invalid Content-Range: $contentRange")
            }

        val eocdFetchSize = min(fileSize, 65535L + 22L)

        val eocdRequest =
            Request.Builder()
                .url(url)
                .header("Range", "bytes=${fileSize - eocdFetchSize}-${fileSize - 1}")
                .build()

        val eocdBytes =
            chain.proceed(eocdRequest).use { response ->

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

        val cdRequest =
            Request.Builder()
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

                map[name] =
                    ZipEntryInfo(offset, compSize, uncompSize, method, nameLen, extraLen)
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

    companion object {
        private const val MAX_UNCOMPRESSED_SIZE = 50 * 1024 * 1024
    }
}
