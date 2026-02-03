package eu.kanade.tachiyomi.extension.ja.unext

import eu.kanade.tachiyomi.extension.ja.unext.ZipParser.inflateRaw
import eu.kanade.tachiyomi.extension.ja.unext.ZipParser.parseAllCDs
import eu.kanade.tachiyomi.extension.ja.unext.ZipParser.parseEOCD
import eu.kanade.tachiyomi.extension.ja.unext.ZipParser.parseEOCD64
import eu.kanade.tachiyomi.extension.ja.unext.ZipParser.parseLocalFile
import eu.kanade.tachiyomi.network.GET
import kotlinx.serialization.Serializable
import okhttp3.Headers
import okhttp3.OkHttpClient
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder.LITTLE_ENDIAN
import java.util.zip.Inflater
import kotlin.text.Charsets.UTF_8

const val CENTRAL_DIRECTORY_FILE_HEADER_SIGNATURE = 0x02014b50
const val END_OF_CENTRAL_DIRECTORY_SIGNATURE = 0x06054b50
const val END_OF_CENTRAL_DIRECTORY_64_SIGNATURE = 0x06064b50
const val LOCAL_FILE_HEADER_SIGNATURE = 0x04034b50

class EndOfCentralDirectory(
    val centralDirectoryByteSize: BigInteger,
    val centralDirectoryByteOffset: BigInteger,
    val locationInFile: Long,
)

@Serializable
class CentralDirectoryRecord(
    val length: Int,
    val compressedSize: Int,
    val localFileHeaderRelativeOffset: Int,
    val filename: String,
)

class LocalFileHeader(
    val compressedData: ByteArray,
    val compressionMethod: Int,
)

@Serializable
class Zip(
    private val url: String,
    val zipStartOffset: Long,
    private val centralDirectoryRecords: List<CentralDirectoryRecord>,
) {
    fun fetch(path: String, client: OkHttpClient): ByteArray {
        val file = getEntry(path)
            ?: throw Exception("File not found in ZIP: $path")

        val maxLocalFileHeaderSize = 512
        val start = zipStartOffset + file.localFileHeaderRelativeOffset
        val end = start + file.compressedSize + maxLocalFileHeaderSize

        val headersBuilder = Headers.Builder()
            .set("Range", "bytes=$start-$end")
            .build()

        val request = GET(url, headersBuilder)
        val response = client.newCall(request).execute()
        val byteArray = response.body.byteStream().use { it.readBytes() }

        val localFile = parseLocalFile(byteArray, file.compressedSize)
            ?: throw Exception("Failed to parse local file header for $path")

        return if (localFile.compressionMethod == 0) {
            localFile.compressedData
        } else {
            inflateRaw(localFile.compressedData)
        }
    }

    fun getEntry(path: String): CentralDirectoryRecord? = centralDirectoryRecords.find { it.filename == path }
}

class ZipHandler(
    private val url: String,
    private val client: OkHttpClient,
    private val headers: Headers = Headers.Builder().build(),
    private val zipType: String = "zip",
    private val contentLength: BigInteger,
) {
    fun populate(): Zip {
        val endOfCentralDirectory = fetchEndOfCentralDirectory(contentLength, zipType)
        val absoluteCdStart = endOfCentralDirectory.locationInFile.toBigInteger() - endOfCentralDirectory.centralDirectoryByteSize
        val zipStartOffset = (absoluteCdStart - endOfCentralDirectory.centralDirectoryByteOffset).toLong()
        val centralDirectoryRecords = fetchCentralDirectoryRecords(endOfCentralDirectory, zipStartOffset)

        return Zip(
            url,
            zipStartOffset,
            centralDirectoryRecords,
        )
    }

    private fun fetchEndOfCentralDirectory(zipByteLength: BigInteger, zipType: String): EndOfCentralDirectory {
        val eocdMaxBytes = (64 * 1024).toBigInteger()
        val eocdInitialOffset = maxOf(0.toBigInteger(), zipByteLength - eocdMaxBytes)

        val headers = headers.newBuilder()
            .set("Range", "bytes=$eocdInitialOffset-$zipByteLength")
            .build()
        val request = GET(url, headers)

        val response = client.newCall(request).execute()
        val eocdBuffer = response.body.byteStream().use { it.readBytes() }

        val eocd = (if (zipType == "zip64") parseEOCD64(eocdBuffer, eocdInitialOffset.toLong()) else parseEOCD(eocdBuffer, eocdInitialOffset.toLong()))
            ?: throw Exception("Could not find EOCD record")

        return eocd
    }

    private fun fetchCentralDirectoryRecords(eocd: EndOfCentralDirectory, zipStartOffset: Long): List<CentralDirectoryRecord> {
        val cdStart = zipStartOffset.toBigInteger() + eocd.centralDirectoryByteOffset
        val cdEnd = cdStart + eocd.centralDirectoryByteSize

        val headersBuilder = headers.newBuilder()
            .set("Range", "bytes=$cdStart-$cdEnd")
            .build()

        val request = GET(url, headersBuilder)
        val response = client.newCall(request).execute()
        val cdBuffer = response.body.byteStream().use { it.readBytes() }

        return parseAllCDs(cdBuffer)
    }
}

object ZipParser {
    fun parseAllCDs(buffer: ByteArray): List<CentralDirectoryRecord> {
        val cds = ArrayList<CentralDirectoryRecord>()
        val view = ByteBuffer.wrap(buffer).order(LITTLE_ENDIAN)

        var i = 0
        while (i <= buffer.size - 4) {
            val signature = view.getInt(i)
            if (signature == CENTRAL_DIRECTORY_FILE_HEADER_SIGNATURE) {
                val cd = parseCD(buffer.sliceArray(i until buffer.size))
                if (cd != null) {
                    cds.add(cd)
                    i += cd.length - 1
                    continue
                }
            } else if (signature == END_OF_CENTRAL_DIRECTORY_SIGNATURE) {
                break
            }
            i++
        }
        return cds
    }

    fun parseCD(buffer: ByteArray): CentralDirectoryRecord? {
        val minCdLength = 46
        val view = ByteBuffer.wrap(buffer).order(LITTLE_ENDIAN)

        for (i in 0..buffer.size - minCdLength) {
            if (view.getInt(i) == CENTRAL_DIRECTORY_FILE_HEADER_SIGNATURE) {
                val filenameLength = view.getShort(i + 28).toInt()
                val extraFieldLength = view.getShort(i + 30).toInt()
                val fileCommentLength = view.getShort(i + 32).toInt()

                return CentralDirectoryRecord(
                    46 + filenameLength + extraFieldLength + fileCommentLength,
                    view.getInt(i + 20),
                    view.getInt(i + 42),
                    buffer.sliceArray(i + 46 until i + 46 + filenameLength).toString(UTF_8),
                )
            }
        }
        return null
    }

    fun parseEOCD(buffer: ByteArray, bufferStartOffset: Long): EndOfCentralDirectory? {
        val minEocdLength = 22
        val view = ByteBuffer.wrap(buffer).order(LITTLE_ENDIAN)

        for (i in buffer.size - minEocdLength downTo 0) {
            if (view.getInt(i) == END_OF_CENTRAL_DIRECTORY_SIGNATURE) {
                return EndOfCentralDirectory(
                    view.getInt(i + 12).toBigInteger(),
                    view.getInt(i + 16).toBigInteger(),
                    bufferStartOffset + i,
                )
            }
        }
        return null
    }

    fun parseEOCD64(buffer: ByteArray, bufferStartOffset: Long): EndOfCentralDirectory? {
        val minEocdLength = 56
        val view = ByteBuffer.wrap(buffer).order(LITTLE_ENDIAN)

        for (i in buffer.size - minEocdLength downTo 0) {
            if (view.getInt(i) == END_OF_CENTRAL_DIRECTORY_64_SIGNATURE) {
                return EndOfCentralDirectory(
                    view.getLong(i + 40).toBigInteger(),
                    view.getLong(i + 48).toBigInteger(),
                    bufferStartOffset + i,
                )
            }
        }
        return null
    }

    fun parseLocalFile(buffer: ByteArray, compressedSizeOverride: Int = 0): LocalFileHeader? {
        val minLocalFileLength = 30
        val view = ByteBuffer.wrap(buffer).order(LITTLE_ENDIAN)

        for (i in 0..buffer.size - minLocalFileLength) {
            if (view.getInt(i) == LOCAL_FILE_HEADER_SIGNATURE) {
                val filenameLength = view.getShort(i + 26).toInt() and 0xFFFF
                val extraFieldLength = view.getShort(i + 28).toInt() and 0xFFFF

                val bitflags = view.getShort(i + 6).toInt() and 0xFFFF
                val hasDataDescriptor = (bitflags shr 3) and 1 != 0

                val headerEndOffset = i + 30 + filenameLength + extraFieldLength
                val regularCompressedSize = view.getInt(i + 18)

                val compressedData = if (hasDataDescriptor) {
                    buffer.copyOfRange(headerEndOffset, headerEndOffset + compressedSizeOverride)
                } else {
                    buffer.copyOfRange(headerEndOffset, headerEndOffset + regularCompressedSize)
                }

                return LocalFileHeader(
                    compressedData,
                    view.getShort(i + 8).toInt(),
                )
            }
        }
        return null
    }

    fun inflateRaw(compressedData: ByteArray): ByteArray {
        val inflater = Inflater(true)
        inflater.setInput(compressedData)
        val buffer = ByteArray(8192)
        val output = ByteArrayOutputStream()
        try {
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                if (count > 0) output.write(buffer, 0, count)
                if (inflater.needsInput()) break
            }
        } catch (_: Exception) {
        } finally {
            inflater.end()
            output.close()
        }
        return output.toByteArray()
    }
}
