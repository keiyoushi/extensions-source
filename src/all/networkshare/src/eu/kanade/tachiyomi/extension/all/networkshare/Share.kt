package eu.kanade.tachiyomi.extension.all.networkshare

import keiyoushi.zip.Entry
import keiyoushi.zip.ZipDirectory
import keiyoushi.zip.dataRange
import keiyoushi.zip.readEntry
import okio.BufferedSource
import okio.Source
import java.util.zip.ZipException

/** A folder on a file server. Paths are `/`-separated and relative to that folder; `""` is the folder itself. */
interface Share {
    suspend fun list(path: String): List<RemoteFile>

    /** The file's content, or the bytes in [range] (inclusive, may run past the end); the caller closes it. */
    suspend fun read(path: String, range: LongRange? = null): BufferedSource

    suspend fun zipDirectory(path: String): ZipDirectory

    /** The address of [path] as the user would type it. */
    fun url(path: String): String
}

class RemoteFile(
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
)

/**
 * One entry's decompressed content; the caller closes it. With [verify], the local header must still carry the
 * entry's name: page urls keep offsets, and the archive may have been replaced since.
 *
 * @throws ZipException if [verify] is set and the archive changed
 */
suspend fun Share.readZipEntry(path: String, entry: Entry, verify: Boolean = false): Source {
    val raw = read(path, entry.dataRange)
    if (verify && !raw.peek().hasLocalHeader(entry.name)) {
        raw.close()
        throw ZipException("${entry.name} moved in $path")
    }
    return readEntry(raw, entry.compressedSize, entry.method)
}

/** Whether this starts with the local header of [name], decoded as UTF-8 like the central directory's names */
private fun BufferedSource.hasLocalHeader(name: String): Boolean {
    if (!request(LOCAL_HEADER_LENGTH.toLong()) || readIntLe() != LOCAL_HEADER_SIGNATURE) return false
    skip(LOCAL_HEADER_NAME_LENGTH_OFFSET - 4L)
    val nameLength = readShortLe().toLong() and 0xFFFF
    skip(LOCAL_HEADER_LENGTH - LOCAL_HEADER_NAME_LENGTH_OFFSET - 2L)
    return request(nameLength) && readUtf8(nameLength) == name
}

private const val LOCAL_HEADER_SIGNATURE = 0x04034b50
private const val LOCAL_HEADER_LENGTH = 30
private const val LOCAL_HEADER_NAME_LENGTH_OFFSET = 26
