package eu.kanade.tachiyomi.extension.all.pandachaika

import eu.kanade.tachiyomi.extension.all.pandachaika.ZipParser.inflateRaw
import eu.kanade.tachiyomi.extension.all.pandachaika.ZipParser.parseAllCDs
import eu.kanade.tachiyomi.extension.all.pandachaika.ZipParser.parseEOCD
import eu.kanade.tachiyomi.extension.all.pandachaika.ZipParser.parseEOCD64
import eu.kanade.tachiyomi.extension.all.pandachaika.ZipParser.parseLocalFile
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
    private val centralDirectoryRecords: List<CentralDirectoryRecord>,
) {
    fun files(): List<String> {
        return centralDirectoryRecords.map {
            it.filename
        }
    }

    fun fetch(path: String, client: OkHttpClient): ByteArray {
        val file = centralDirectoryRecords.find { it.filename == path }
            ?: throw Exception("File not found in  ZIP: $path")

        val MAX_LOCAL_FILE_HEADER_SIZE = 256 + 32 + 30 + 100

        val headersBuilder = Headers.Builder()
            .set(
                "Range",
                "bytes=${file.localFileHeaderRelativeOffset}-${
                file.localFileHeaderRelativeOffset +
                    file.compressedSize +
                    MAX_LOCAL_FILE_HEADER_SIZE
                }",
            ).build()

        val request = GET(url, headersBuilder)

        val response = client.newCall(request).execute()

        val byteArray = response.body.byteStream().use { it.readBytes() }

        val localFile = parseLocalFile(byteArray, file.compressedSize)
            ?: throw Exception("Failed to parse local file header in ZIP")

        return if (localFile.compressionMethod == 0) {
            localFile.compressedData
        } else {
            inflateRaw(localFile.compressedData)
        }
    }
}

class ZipHandler(
    private val url: String,
    private val client: OkHttpClient,
    private val additionalHeaders: Headers = Headers.Builder().build(),
    private val zipType: String = "zip",
    private val contentLength: BigInteger,
) {
    fun populate(): Zip {
        val endOfCentralDirectory = fetchEndOfCentralDirectory(contentLength, zipType)
        val centralDirectoryRecords = fetchCentralDirectoryRecords(endOfCentralDirectory)

        return Zip(
            url,
            centralDirectoryRecords,
        )
    }

    private fun fetchEndOfCentralDirectory(zipByteLength: BigInteger, zipType: String): EndOfCentralDirectory {
        val EOCD_MAX_BYTES = 128.toBigInteger()
        val eocdInitialOffset = maxOf(0.toBigInteger(), zipByteLength - EOCD_MAX_BYTES)

        val headers = additionalHeaders
            .newBuilder()
            .set("Range", "bytes=$eocdInitialOffset-$zipByteLength")
            .build()
        val request = GET(url, headers)

        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            throw Exception("Could not fetch ZIP: HTTP status ${response.code}")
        }

        val eocdBuffer = response.body.byteStream().use { it.readBytes() }

        if (eocdBuffer.isEmpty()) throw Exception("Could not get Range request to start looking for EOCD")

        val eocd =
            (if (zipType == "zip64") parseEOCD64(eocdBuffer) else parseEOCD(eocdBuffer))
                ?: throw Exception("Could not get EOCD record of the ZIP")

        return eocd
    }

    private fun fetchCentralDirectoryRecords(endOfCentralDirectory: EndOfCentralDirectory): List<CentralDirectoryRecord> {
        val headersBuilder = Headers.Builder()
            .set(
                "Range",
                "bytes=${endOfCentralDirectory.centralDirectoryByteOffset}-${
                endOfCentralDirectory.centralDirectoryByteOffset +
                    endOfCentralDirectory.centralDirectoryByteSize
                }",
            ).build()

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
        val MIN_CD_LENGTH = 46
        val view = ByteBuffer.wrap(buffer).order(LITTLE_ENDIAN)

        for (i in 0..buffer.size - MIN_CD_LENGTH) {
            if (view.getInt(i) == CENTRAL_DIRECTORY_FILE_HEADER_SIGNATURE) {
                val filenameLength = view.getShort(i + 28).toInt()
                val extraFieldLength = view.getShort(i + 30).toInt()
                val fileCommentLength = view.getShort(i + 32).toInt()

                return CentralDirectoryRecord(
                    length = 46 + filenameLength + extraFieldLength + fileCommentLength,
                    compressedSize = view.getInt(i + 20),
                    localFileHeaderRelativeOffset = view.getInt(i + 42),
                    filename = buffer.sliceArray(i + 46 until i + 46 + filenameLength).toString(UTF_8),
                )
            }
        }
        return null
    }

    fun parseEOCD(buffer: ByteArray): EndOfCentralDirectory? {
        val MIN_EOCD_LENGTH = 22
        val view = ByteBuffer.wrap(buffer).order(LITTLE_ENDIAN)

        for (i in 0 until buffer.size - MIN_EOCD_LENGTH + 1) {
            if (view.getInt(i) == END_OF_CENTRAL_DIRECTORY_SIGNATURE) {
                return EndOfCentralDirectory(
                    centralDirectoryByteSize = view.getInt(i + 12).toBigInteger(),
                    centralDirectoryByteOffset = view.getInt(i + 16).toBigInteger(),
                )
            }
        }
        return null
    }

    fun parseEOCD64(buffer: ByteArray): EndOfCentralDirectory? {
        val MIN_EOCD_LENGTH = 56
        val view = ByteBuffer.wrap(buffer).order(LITTLE_ENDIAN)

        for (i in 0 until buffer.size - MIN_EOCD_LENGTH + 1) {
            if (view.getInt(i) == END_OF_CENTRAL_DIRECTORY_64_SIGNATURE) {
                return EndOfCentralDirectory(
                    centralDirectoryByteSize = view.getLong(i + 40).toBigInteger(),
                    centralDirectoryByteOffset = view.getLong(i + 48).toBigInteger(),
                )
            }
        }
        return null
    }

    fun parseLocalFile(buffer: ByteArray, compressedSizeOverride: Int = 0): LocalFileHeader? {
        val MIN_LOCAL_FILE_LENGTH = 30

        val view = ByteBuffer.wrap(buffer).order(LITTLE_ENDIAN)

        for (i in 0..buffer.size - MIN_LOCAL_FILE_LENGTH) {
            if (view.getInt(i) == LOCAL_FILE_HEADER_SIGNATURE) {
                val filenameLength = view.getShort(i + 26).toInt() and 0xFFFF
                val extraFieldLength = view.getShort(i + 28).toInt() and 0xFFFF

                val bitflags = view.getShort(i + 6).toInt() and 0xFFFF
                val hasDataDescriptor = (bitflags shr 3) and 1 != 0

                val headerEndOffset = i + 30 + filenameLength + extraFieldLength
                val regularCompressedSize = view.getInt(i + 18)

                val compressedData = if (hasDataDescriptor) {
                    buffer.copyOfRange(
                        headerEndOffset,
                        headerEndOffset + compressedSizeOverride,
                    )
                } else {
                    buffer.copyOfRange(
                        headerEndOffset,
                        headerEndOffset + regularCompressedSize,
                    )
                }

                return LocalFileHeader(
                    compressedData = compressedData,
                    compressionMethod = view.getShort(i + 8).toInt(),
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
                if (count > 0) {
                    output.write(buffer, 0, count)
                }
            }
        } catch (e: Exception) {
            throw Exception("Invalid compressed data format: ${e.message}", e)
        } finally {
            inflater.end()
            output.close()
        }

        return output.toByteArray()
    }
}
