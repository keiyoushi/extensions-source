package keiyoushi.zip

import keiyoushi.utils.readLongLittleEndian
import keiyoushi.utils.readUIntLittleEndian
import keiyoushi.utils.readUShortLittleEndian
import okhttp3.Request
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.InflaterSource
import okio.Source
import okio.buffer
import java.io.EOFException
import java.nio.charset.Charset
import java.util.zip.CRC32
import java.util.zip.Inflater
import java.util.zip.ZipException

private const val EOCD_SIG = 0x06054b50L
private const val EOCD64_SIG = 0x06064b50L
private const val CDH_SIG = 0x02014b50L
private const val LFH_SIG = 0x04034b50L
private const val EOCD_MIN_LEN = 22
private const val EOCD64_MIN_LEN = 56
private const val EOCD64_LOCATOR_LEN = 20
private const val CDH_MIN_LEN = 46
private const val LFH_MIN_LEN = 30
private const val METHOD_STORED = 0
private const val METHOD_DEFLATE = 8

// 0xFFFFFFFF field value means the real value lives in a ZIP64 structure
private const val ZIP64_SENTINEL = 0xFFFFFFFFL
private const val ZIP64_EXTRA_ID = 0x0001

// General purpose bits 0 and 11: the entry is encrypted, the name is UTF-8
private const val FLAG_ENCRYPTED = 0x0001
private const val FLAG_UTF8 = 0x0800

// Info-ZIP Unicode Path extra field: a UTF-8 copy of a name stored in another charset
private const val UNICODE_PATH_EXTRA_ID = 0x7075
private const val UNICODE_PATH_VERSION = 1

// Offsets of the fields within each record, from APPNOTE.TXT section 4.3
private const val EOCD_CD_SIZE_OFFSET = 12
private const val EOCD_CD_START_OFFSET = 16
private const val EOCD_COMMENT_LEN_OFFSET = 20

// The ZIP64 record gives the size of the rest of it, which can carry extensible data
private const val EOCD64_RECORD_SIZE_OFFSET = 4
private const val EOCD64_CD_SIZE_OFFSET = 40
private const val EOCD64_CD_START_OFFSET = 48

private const val CDH_FLAGS_OFFSET = 8
private const val CDH_METHOD_OFFSET = 10
private const val CDH_COMPRESSED_SIZE_OFFSET = 20
private const val CDH_UNCOMPRESSED_SIZE_OFFSET = 24
private const val CDH_NAME_LEN_OFFSET = 28
private const val CDH_EXTRA_LEN_OFFSET = 30
private const val CDH_COMMENT_LEN_OFFSET = 32
private const val CDH_LOCAL_HEADER_START_OFFSET = 42

private const val LFH_FLAGS_OFFSET = 6
private const val LFH_NAME_LEN_OFFSET = 26
private const val LFH_EXTRA_LEN_OFFSET = 28

// Extra field: a 2-byte id and a 2-byte data size, then the data
private const val EXTRA_ID_OFFSET = 0
private const val EXTRA_SIZE_OFFSET = 2
private const val EXTRA_HEADER_LEN = 4

// Unicode Path data: a 1-byte version and the 4-byte CRC-32 of the stored name, then the UTF-8 name
private const val UNICODE_PATH_VERSION_OFFSET = 0
private const val UNICODE_PATH_NAME_CRC_OFFSET = 1
private const val UNICODE_PATH_NAME_OFFSET = 5

/** Maximum trailing bytes to scan for the End Of Central Directory record (22-byte record + 65 535-byte max comment). */
const val MAX_EOCD_SEARCH = EOCD_MIN_LEN + 0xFFFF + 1 // 65558

/**
 * Bytes to fetch ahead of an entry's data for its local file header when only the entry's offset and size are known.
 * [parseCentralDirectory] adds the entry's name and extra field to it, since the local ones can be longer.
 */
const val MAX_LOCAL_FILE_HEADER = 512

/**
 * Location of the central directory within the archive.
 *
 * @property cdOffset central-directory offset recorded in the EOCD, relative to the ZIP start
 * @property cdSize total size of the central directory, in bytes
 * @property recordOffset tail-relative offset of the record that ends the directory; with the tail's absolute start it locates a prepended ZIP's base
 */
class Eocd(
    val cdOffset: Long,
    val cdSize: Long,
    val recordOffset: Int,
)

/**
 * A single central-directory entry.
 *
 * @property name the entry path, decoded as UTF-8 when the archive flags it so, else with the charset given to [parseCentralDirectory]
 * @property method the compression method (STORED or DEFLATE)
 * @property compressedSize the on-disk size of the entry's data, in bytes
 * @property localHeaderOffset offset of the entry's local file header; ZIP-relative from [parseCentralDirectory], absolute from [zipDirectory]
 * @property localHeaderAllowance bytes to fetch ahead of the data for the local file header, see [dataRange]
 */
class Entry(
    val name: String,
    val method: Int,
    val compressedSize: Long,
    val localHeaderOffset: Long,
    val localHeaderAllowance: Int = MAX_LOCAL_FILE_HEADER,
)

/**
 * Byte range to range-fetch to read an entry: its data plus [localHeaderAllowance] bytes ahead of it for the local file
 * header. The offset must be absolute, as resolved by [zipDirectory]. Useful when only the raw fields are on hand rather
 * than an [Entry]; keep [Entry.localHeaderAllowance] with them for entries with long names.
 *
 * @param localHeaderOffset absolute offset of the entry's local file header
 * @param compressedSize the entry's on-disk data size
 * @param localHeaderAllowance bytes to fetch for the local file header, from [Entry.localHeaderAllowance]
 * @return the inclusive range covering the local header and the entry's data
 */
fun dataRange(
    localHeaderOffset: Long,
    compressedSize: Long,
    localHeaderAllowance: Int = MAX_LOCAL_FILE_HEADER,
): LongRange = localHeaderOffset..localHeaderOffset + localHeaderAllowance + compressedSize

/** Byte range to range-fetch to read this entry, via [dataRange]; assumes an absolute offset, as from [zipDirectory]. */
val Entry.dataRange: LongRange
    get() = dataRange(localHeaderOffset, compressedSize, localHeaderAllowance)

/**
 * A parsed central directory with absolute file offsets.
 *
 * @property entries the entries, with absolute [Entry.localHeaderOffset]
 * @property cdOffset absolute offset of the central directory
 */
class ZipDirectory(
    val entries: List<Entry>,
    val cdOffset: Long,
)

/**
 * Locates the End Of Central Directory in [tail], resolving the directory offset and size from the
 * ZIP64 EOCD record when they are ZIP64.
 *
 * @param tail the final bytes of the archive, ending exactly at its end and long enough to contain the End Of Central Directory record
 * @return the parsed [Eocd]
 * @throws ZipException if no valid EOCD record is present in [tail], or a ZIP64 one it points to is missing
 */
fun findEocd(tail: ByteArray): Eocd {
    for (i in tail.size - EOCD_MIN_LEN downTo 0) {
        if (tail.readUIntLittleEndian(i) != EOCD_SIG) continue
        val commentLen = tail.readUShortLittleEndian(i + EOCD_COMMENT_LEN_OFFSET)
        if (i + EOCD_MIN_LEN + commentLen != tail.size) continue

        var cdSize = tail.readUIntLittleEndian(i + EOCD_CD_SIZE_OFFSET)
        var cdOffset = tail.readUIntLittleEndian(i + EOCD_CD_START_OFFSET)
        var recordOffset = i

        if (cdOffset == ZIP64_SENTINEL || cdSize == ZIP64_SENTINEL) {
            // The ZIP64 record ends where its 20-byte locator, just before this record, starts
            val j = findZip64Record(tail, end = i - EOCD64_LOCATOR_LEN) ?: throw ZipException("ZIP64 end of central directory record not found")
            cdSize = tail.readLongLittleEndian(j + EOCD64_CD_SIZE_OFFSET)
            cdOffset = tail.readLongLittleEndian(j + EOCD64_CD_START_OFFSET)
            recordOffset = j
        }
        return Eocd(cdOffset, cdSize, recordOffset)
    }
    throw ZipException("End of central directory record not found")
}

/** Returns the offset in [tail] of the ZIP64 record whose size says it ends at [end], or null if there is none. */
private fun findZip64Record(tail: ByteArray, end: Int): Int? = (end - EOCD64_MIN_LEN downTo 0).firstOrNull { j ->
    tail.readUIntLittleEndian(j) == EOCD64_SIG &&
        j + EOCD64_RECORD_SIZE_OFFSET + Long.SIZE_BYTES + tail.readLongLittleEndian(j + EOCD64_RECORD_SIZE_OFFSET) == end.toLong()
}

/**
 * Parses central-directory bytes into their [Entry], resolving ZIP64 sizes and offsets from each entry's extra field.
 *
 * Names flagged as UTF-8 (general purpose bit 11), or carrying an Info-ZIP Unicode Path extra field that matches them,
 * are read as UTF-8 whatever [charset] is.
 *
 * @param cd the central-directory bytes, as located by [findEocd]
 * @param charset the charset of names not flagged as UTF-8, e.g. `Charset.forName("MS932")` for archives made by Japanese tools
 * @return the entries in directory order, with ZIP-relative offsets
 * @throws ZipException if a header runs past the end of [cd]
 */
fun parseCentralDirectory(cd: ByteArray, charset: Charset = Charsets.UTF_8): List<Entry> {
    val entries = ArrayList<Entry>()
    var p = 0
    while (p + CDH_MIN_LEN <= cd.size && cd.readUIntLittleEndian(p) == CDH_SIG) {
        val flags = cd.readUShortLittleEndian(p + CDH_FLAGS_OFFSET)
        val nameLen = cd.readUShortLittleEndian(p + CDH_NAME_LEN_OFFSET)
        val extraLen = cd.readUShortLittleEndian(p + CDH_EXTRA_LEN_OFFSET)
        val commentLen = cd.readUShortLittleEndian(p + CDH_COMMENT_LEN_OFFSET)
        val nameStart = p + CDH_MIN_LEN
        val headerEnd = nameStart + nameLen + extraLen + commentLen
        if (headerEnd > cd.size) throw ZipException("Truncated central directory")
        val storedName = cd.copyOfRange(nameStart, nameStart + nameLen)
        val extra = cd.copyOfRange(nameStart + nameLen, nameStart + nameLen + extraLen)

        var compressedSize = cd.readUIntLittleEndian(p + CDH_COMPRESSED_SIZE_OFFSET)
        val uncompressedSize = cd.readUIntLittleEndian(p + CDH_UNCOMPRESSED_SIZE_OFFSET)
        var localHeaderOffset = cd.readUIntLittleEndian(p + CDH_LOCAL_HEADER_START_OFFSET)

        if (compressedSize == ZIP64_SENTINEL || localHeaderOffset == ZIP64_SENTINEL || uncompressedSize == ZIP64_SENTINEL) {
            // The ZIP64 field holds, in order, only the sizes and offset whose 32-bit field is the sentinel
            val zip64 = extra.extraField(ZIP64_EXTRA_ID)
            if (zip64 != null) {
                var z = 0
                if (uncompressedSize == ZIP64_SENTINEL) z += Long.SIZE_BYTES
                if (compressedSize == ZIP64_SENTINEL) {
                    compressedSize = zip64.readLongLittleEndian(z)
                    z += Long.SIZE_BYTES
                }
                if (localHeaderOffset == ZIP64_SENTINEL) {
                    localHeaderOffset = zip64.readLongLittleEndian(z)
                }
            }
        }

        entries += Entry(
            name = if (flags and FLAG_UTF8 != 0) {
                String(storedName, Charsets.UTF_8)
            } else {
                extra.extraField(UNICODE_PATH_EXTRA_ID)?.let { unicodePath(it, storedName) } ?: String(storedName, charset)
            },
            method = cd.readUShortLittleEndian(p + CDH_METHOD_OFFSET),
            compressedSize = compressedSize,
            localHeaderOffset = localHeaderOffset,
            localHeaderAllowance = LFH_MIN_LEN + nameLen + extraLen + MAX_LOCAL_FILE_HEADER,
        )
        p = headerEnd
    }
    return entries
}

/** Returns the data of the extra field [id] in this extra block, or null if the block has no such field. */
private fun ByteArray.extraField(id: Int): ByteArray? {
    var p = 0
    while (p + EXTRA_HEADER_LEN <= size) {
        val dataStart = p + EXTRA_HEADER_LEN
        val dataEnd = dataStart + readUShortLittleEndian(p + EXTRA_SIZE_OFFSET)
        if (dataEnd > size) return null
        if (readUShortLittleEndian(p + EXTRA_ID_OFFSET) == id) return copyOfRange(dataStart, dataEnd)
        p = dataEnd
    }
    return null
}

/**
 * The UTF-8 name in an Info-ZIP Unicode Path [field]. Null when the field's CRC-32 doesn't match [storedName], which
 * means the stored name was changed after the field was written.
 */
private fun unicodePath(field: ByteArray, storedName: ByteArray): String? {
    if (field.size < UNICODE_PATH_NAME_OFFSET || field[UNICODE_PATH_VERSION_OFFSET].toInt() != UNICODE_PATH_VERSION) return null
    val storedNameCrc = CRC32().apply { update(storedName) }.value
    if (field.readUIntLittleEndian(UNICODE_PATH_NAME_CRC_OFFSET) != storedNameCrc) return null
    return String(field, UNICODE_PATH_NAME_OFFSET, field.size - UNICODE_PATH_NAME_OFFSET, Charsets.UTF_8)
}

/**
 * Where an archive's central directory lives, resolved from its tail by [locateCentralDirectory].
 *
 * @property bytes the directory bytes when the tail already contained them, else null
 * @property range the range to fetch when [bytes] is null
 */
internal class CentralDirectory(
    val bytes: ByteArray?,
    val range: LongRange,
    private val zipStart: Long,
    private val cdOffset: Long,
) {
    /** Parses [cd] into a [ZipDirectory] with absolute offsets, decoding names not flagged as UTF-8 with [charset]. */
    fun parse(cd: ByteArray, charset: Charset): ZipDirectory {
        val entries = parseCentralDirectory(cd, charset).let { list ->
            if (zipStart == 0L) list else list.map { Entry(it.name, it.method, it.compressedSize, zipStart + it.localHeaderOffset, it.localHeaderAllowance) }
        }
        return ZipDirectory(entries, zipStart + cdOffset)
    }
}

internal fun locateCentralDirectory(tail: ByteArray, totalSize: Long): CentralDirectory {
    val tailStart = totalSize - tail.size
    val eocd = findEocd(tail)

    val zipStart = tailStart + eocd.recordOffset - eocd.cdSize - eocd.cdOffset
    val cdWithinTail = eocd.recordOffset - eocd.cdSize
    val bytes = if (cdWithinTail >= 0) {
        val from = cdWithinTail.toInt()
        tail.copyOfRange(from, from + eocd.cdSize.toInt())
    } else {
        null
    }

    val cdStart = zipStart + eocd.cdOffset
    return CentralDirectory(bytes, cdStart..<cdStart + eocd.cdSize, zipStart, eocd.cdOffset)
}

/**
 * Reads one entry from [source], which must start at the entry's local file header and supply at least
 * the header plus [compressedSize] bytes. The result is lazy and closing it closes [source]; STORED
 * data passes through, DEFLATE is inflated.
 *
 * @param source a source positioned at the entry's local file header
 * @param compressedSize the entry's on-disk data size, from [Entry.compressedSize]
 * @param method the compression method, from [Entry.method]
 * @param decode transform applied to the bounded raw payload before inflation; defaults to identity
 * @return a [Source] yielding the entry's decompressed bytes
 * @throws ZipException if [method] is unsupported, the local header signature is missing or the entry is encrypted
 */
fun readEntry(
    source: BufferedSource,
    compressedSize: Long,
    method: Int,
    decode: (BufferedSource) -> Source = { it },
): Source {
    if (method != METHOD_STORED && method != METHOD_DEFLATE) throw ZipException("Unsupported ZIP method: $method")
    skipLocalHeader(source)
    val payload = decode(source.fixedLength(compressedSize).buffer())
    return if (method == METHOD_DEFLATE) InflaterSource(payload, Inflater(true)) else payload
}

private fun skipLocalHeader(source: BufferedSource) {
    val header = source.readByteArray(LFH_MIN_LEN.toLong())
    if (header.readUIntLittleEndian(0) != LFH_SIG) throw ZipException("Not a local file header")
    if (header.readUShortLittleEndian(LFH_FLAGS_OFFSET) and FLAG_ENCRYPTED != 0) throw ZipException("Encrypted ZIP entries aren't supported")
    source.skip((header.readUShortLittleEndian(LFH_NAME_LEN_OFFSET) + header.readUShortLittleEndian(LFH_EXTRA_LEN_OFFSET)).toLong())
}

/**
 * Wraps this source to yield exactly [byteCount] bytes, throwing [EOFException] if it ends sooner.
 * Reading stops at [byteCount] even if more is available, so it also truncates a longer source.
 *
 * @param byteCount the exact number of bytes to yield
 * @return a source bounded to [byteCount] bytes
 */
fun Source.fixedLength(byteCount: Long): Source = FixedLengthSource(this, byteCount)

private class FixedLengthSource(
    delegate: Source,
    private var remaining: Long,
) : ForwardingSource(delegate) {
    override fun read(sink: Buffer, byteCount: Long): Long {
        if (remaining == 0L) return -1L
        val read = super.read(sink, minOf(byteCount, remaining))
        if (read == -1L) throw EOFException("Source ended $remaining byte(s) early")
        remaining -= read
        return read
    }
}

/** Sets the `Range` header to the inclusive byte [range]. */
fun Request.Builder.range(range: LongRange): Request.Builder = header("Range", "bytes=${range.first}-${range.last}")
