package eu.kanade.tachiyomi.extension.all.pandachaika

import kotlinx.serialization.Serializable
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder.BIG_ENDIAN
import java.nio.ByteOrder.LITTLE_ENDIAN
import java.util.zip.Inflater
import kotlin.text.Charsets.UTF_8

const val SIG_CD: Int = 0x504b0102
const val SIG_LOCAL_FILE_HEADER: Int = 0x504b0304
const val SIG_EOCD: Int = 0x504b0506

class RemoteZipError(message: String) : Exception(message)

data class EndOfCentralDirectory(
    val cdDisk: Int,
    val centralDirectoryByteSize: Int,
    val centralDirectoryByteOffset: Int,
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
class RemoteZip(
    private val url: String,
    private val centralDirectoryRecords: List<CentralDirectoryRecord>,
    private val method: String,
) {
    fun files(): List<String> {
        return centralDirectoryRecords.map {
            it.filename
        }
    }

    fun fetch(path: String): ByteArray {
        val file = centralDirectoryRecords.find { it.filename == path }
            ?: throw RemoteZipError("File not found in remote ZIP: $path")

        val MAX_LOCAL_FILE_HEADER_SIZE = 256 + 32 + 30 + 100

        val headersBuilder = Headers.Builder()
            .set(
                "Range",
                "bytes=${file.localFileHeaderRelativeOffset}-${
                file.localFileHeaderRelativeOffset +
                    file.compressedSize +
                    MAX_LOCAL_FILE_HEADER_SIZE
                }",
            )

        val request = Request.Builder()
            .url(url)
            .method(method, null)
            .headers(headersBuilder.build())
            .build()

        val response = OkHttpClient().newCall(request).execute()

        val byteArray = response.body.byteStream().use { it.readBytes() }

        val localFile = parseOneLocalFile(byteArray, file.compressedSize)
            ?: throw RemoteZipError("Failed to parse local file header in remote ZIP")

        return if (localFile.compressionMethod == 0) {
            localFile.compressedData
        } else {
            inflateRaw(localFile.compressedData)
        }
    }
}

class RemoteZipPointer(
    private val url: HttpUrl,
    private val client: OkHttpClient = OkHttpClient(),
    private val additionalHeaders: Headers = Headers.Builder().build(),
    private val method: String = "GET",
) {
    fun populate(): RemoteZip {
        val request = Request.Builder()
            .url(url)
            .headers(additionalHeaders)
            .method("HEAD", null)
            .build()

        val response = client.newCall(request).execute()

        val contentLengthRaw = response.header("content-length")
            ?: throw RemoteZipError("Could not get Content-Length of URL")

        val contentLength = contentLengthRaw.toInt()
        val endOfCentralDirectory = fetchEndOfCentralDirectory(contentLength)
        val centralDirectoryRecords = fetchCentralDirectoryRecords(endOfCentralDirectory)

        return RemoteZip(
            url.toString(),
            centralDirectoryRecords,
            method,
        )
    }

    private fun fetchEndOfCentralDirectory(zipByteLength: Int): EndOfCentralDirectory {
        val EOCD_MAX_BYTES = 128
        val eocdInitialOffset = maxOf(0, zipByteLength - EOCD_MAX_BYTES)

        val request = Request.Builder()
            .url(url)
            .method(method, null)
            .headers(additionalHeaders)
            .addHeader("Range", "bytes=$eocdInitialOffset-$zipByteLength")
            .build()

        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            throw RemoteZipError("Could not fetch remote ZIP: HTTP status ${response.code}")
        }

        val eocdBuffer = response.body.byteStream().use { it.readBytes() }

        // throw Exception("Maybe it's here")

        if (eocdBuffer.isEmpty()) throw RemoteZipError("Could not get Range request to start looking for EOCD")

        val eocd = parseOneEOCD(eocdBuffer) ?: throw RemoteZipError("Could not get EOCD record of remote ZIP")

        if (eocd.cdDisk == 0xffff) {
            throw RemoteZipError("ZIP file not supported: could not get EOCD record or ZIP64")
        }

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
            )

        val request = Request.Builder()
            .url(url)
            .method(method, null)
            .headers(headersBuilder.build())
            .build()

        val response = client.newCall(request).execute()

        val cdBuffer = response.body.byteStream().use { it.readBytes() }

        return parseAllCDs(cdBuffer)
    }
}

fun parseAllCDs(buffer: ByteArray): List<CentralDirectoryRecord> {
    val cds = ArrayList<CentralDirectoryRecord>()
    val view = ByteBuffer.wrap(buffer)

    var i = 0
    while (i <= buffer.size - 4) {
        val signature = view.getInt(i) // Assuming signature is 4 bytes integer
        if (signature == SIG_CD) {
            val cd = parseOneCD(buffer.sliceArray(i until buffer.size)) // Implement parseOneCD function
            if (cd != null) {
                cds.add(cd)
                i += cd.length - 1
                continue
            }
        } else if (signature == SIG_EOCD) {
            break
        }
        i++
    }

    return cds
}

fun parseOneCD(buffer: ByteArray): CentralDirectoryRecord? {
    val MIN_CD_LENGTH = 46
    val view = ByteBuffer.wrap(buffer)

    for (i in 0..buffer.size - MIN_CD_LENGTH) {
        if (view.getInt(i) == SIG_CD) {
            val filenameLength = view.order(LITTLE_ENDIAN).getShort(i + 28) // n
            val extraFieldLength = view.order(LITTLE_ENDIAN).getShort(i + 30) // m
            val fileCommentLength = view.order(LITTLE_ENDIAN).getShort(i + 32) // k

            return CentralDirectoryRecord(
                length = 46 + filenameLength + extraFieldLength + fileCommentLength,
                compressedSize = view.order(LITTLE_ENDIAN).getInt(i + 20),
                localFileHeaderRelativeOffset = view.order(LITTLE_ENDIAN).getInt(i + 42),
                filename = buffer.sliceArray(i + 46 until i + 46 + filenameLength).toString(UTF_8),
            )
        }
    }
    return null
}

fun parseOneEOCD(buffer: ByteArray): EndOfCentralDirectory? {
    val MIN_EOCD_LENGTH = 22
    val view = ByteBuffer.wrap(buffer)

    for (i in 0 until buffer.size - MIN_EOCD_LENGTH + 1) {
        if (view.order(BIG_ENDIAN).getInt(i) == SIG_EOCD) {
            return EndOfCentralDirectory(
                cdDisk = view.order(LITTLE_ENDIAN).getShort(i + 6).toInt(),
                centralDirectoryByteSize = view.order(LITTLE_ENDIAN).getInt(i + 12),
                centralDirectoryByteOffset = view.order(LITTLE_ENDIAN).getInt(i + 16),
            )
        }
    }
    return null
}

fun parseOneLocalFile(buffer: ByteArray, compressedSizeOverride: Int = 0): LocalFileHeader? {
    val MIN_LOCAL_FILE_LENGTH = 30

    val view = ByteBuffer.wrap(buffer)

    // Seek to first local file header
    for (i in 0..buffer.size - MIN_LOCAL_FILE_LENGTH) {
        if (view.order(BIG_ENDIAN).getInt(i) == SIG_LOCAL_FILE_HEADER) {
            val filenameLength = view.order(LITTLE_ENDIAN).getShort(i + 26).toInt() and 0xFFFF
            val extraFieldLength = view.order(LITTLE_ENDIAN).getShort(i + 28).toInt() and 0xFFFF

            val bitflags = view.order(LITTLE_ENDIAN).getShort(i + 6).toInt() and 0xFFFF
            val hasDataDescriptor = (bitflags shr 3) and 1 != 0

            val headerEndOffset = i + 30 + filenameLength + extraFieldLength
            val regularCompressedSize = view.order(LITTLE_ENDIAN).getInt(i + 18)

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
                compressionMethod = view.order(LITTLE_ENDIAN).getShort(i + 8).toInt(),
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
            if (count <= 0 && inflater.finished()) {
                break
            }
            output.write(buffer, 0, count)
        }
    } catch (e: Exception) {
        throw Exception("Invalid compressed data format: ${e.message}", e)
    } finally {
        inflater.end()
        output.close()
    }

    return output.toByteArray()
}
