package keiyoushi.zip

import eu.kanade.tachiyomi.network.GET
import keiyoushi.utils.readLongLittleEndian
import keiyoushi.utils.readUIntLittleEndian
import keiyoushi.utils.readUShortLittleEndian
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.InflaterSource
import okio.Source
import okio.buffer
import java.io.EOFException
import java.io.IOException
import java.util.zip.Inflater

private const val EOCD_SIG = 0x06054b50L
private const val EOCD64_SIG = 0x06064b50L
private const val CDH_SIG = 0x02014b50L
private const val LFH_SIG = 0x04034b50L
private const val EOCD_MIN_LEN = 22
private const val EOCD64_MIN_LEN = 56
private const val CDH_MIN_LEN = 46
private const val LFH_MIN_LEN = 30
private const val METHOD_STORED = 0
private const val METHOD_DEFLATE = 8

// 0xFFFFFFFF field value means the real value lives in a ZIP64 structure
private const val ZIP64_SENTINEL = 0xFFFFFFFFL
private const val ZIP64_EXTRA_ID = 0x0001

/** Maximum trailing bytes to scan for the End Of Central Directory record (22-byte record + 65 535-byte max comment). */
const val MAX_EOCD_SEARCH = EOCD_MIN_LEN + 0xFFFF + 1 // 65558

/** Bytes to over-fetch ahead of an entry's data to capture its local file header in one range request. */
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
 * @property name the entry path as stored in the archive
 * @property method the compression method (STORED or DEFLATE)
 * @property compressedSize the on-disk size of the entry's data, in bytes
 * @property localHeaderOffset offset of the entry's local file header; ZIP-relative from [parseCentralDirectory], absolute from [readZipDirectory]
 */
class Entry(
    val name: String,
    val method: Int,
    val compressedSize: Long,
    val localHeaderOffset: Long,
)

/**
 * Byte range to range-fetch to read an entry: its data plus [MAX_LOCAL_FILE_HEADER] bytes of headroom
 * for the local file header. The offset must be absolute, as resolved by [readZipDirectory]. Useful
 * when only the raw offset and size are on hand rather than an [Entry].
 *
 * @param localHeaderOffset absolute offset of the entry's local file header
 * @param compressedSize the entry's on-disk data size
 * @return the inclusive range covering the local header and the entry's data
 */
fun dataRange(localHeaderOffset: Long, compressedSize: Long): LongRange = localHeaderOffset..localHeaderOffset + MAX_LOCAL_FILE_HEADER + compressedSize

/** Byte range to range-fetch to read this entry, via [dataRange]; assumes an absolute offset, as from [readZipDirectory]. */
val Entry.dataRange: LongRange
    get() = dataRange(localHeaderOffset, compressedSize)

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
 * @throws IllegalStateException if no valid EOCD record is present in [tail]
 */
fun findEocd(tail: ByteArray): Eocd {
    for (i in tail.size - EOCD_MIN_LEN downTo 0) {
        if (tail.readUIntLittleEndian(i) != EOCD_SIG) continue
        val commentLen = tail.readUShortLittleEndian(i + 20)
        if (i + EOCD_MIN_LEN + commentLen != tail.size) continue

        var cdSize = tail.readUIntLittleEndian(i + 12)
        var cdOffset = tail.readUIntLittleEndian(i + 16)
        var recordOffset = i

        if (cdOffset == ZIP64_SENTINEL || cdSize == ZIP64_SENTINEL) {
            for (j in i - EOCD64_MIN_LEN downTo 0) {
                if (tail.readUIntLittleEndian(j) != EOCD64_SIG) continue
                cdSize = tail.readLongLittleEndian(j + 40)
                cdOffset = tail.readLongLittleEndian(j + 48)
                recordOffset = j
                break
            }
        }
        return Eocd(cdOffset, cdSize, recordOffset)
    }
    throw IllegalStateException("EOCD record not found")
}

/**
 * Parses central-directory bytes into their [Entry], resolving ZIP64 sizes and offsets from each entry's extra field.
 *
 * @param cd the central-directory bytes, as located by [findEocd]
 * @return the entries in directory order, with ZIP-relative offsets
 */
fun parseCentralDirectory(cd: ByteArray): List<Entry> {
    val entries = ArrayList<Entry>()
    var p = 0
    while (p + CDH_MIN_LEN <= cd.size && cd.readUIntLittleEndian(p) == CDH_SIG) {
        val nameLen = cd.readUShortLittleEndian(p + 28)
        val extraLen = cd.readUShortLittleEndian(p + 30)
        val commentLen = cd.readUShortLittleEndian(p + 32)

        var compressedSize = cd.readUIntLittleEndian(p + 20)
        val uncompressedSize = cd.readUIntLittleEndian(p + 24)
        var localHeaderOffset = cd.readUIntLittleEndian(p + 42)

        if (compressedSize == ZIP64_SENTINEL || localHeaderOffset == ZIP64_SENTINEL || uncompressedSize == ZIP64_SENTINEL) {
            var z = zip64ExtraOffset(cd, p + CDH_MIN_LEN + nameLen, extraLen)
            if (z >= 0) {
                if (uncompressedSize == ZIP64_SENTINEL) z += 8
                if (compressedSize == ZIP64_SENTINEL) {
                    compressedSize = cd.readLongLittleEndian(z)
                    z += 8
                }
                if (localHeaderOffset == ZIP64_SENTINEL) {
                    localHeaderOffset = cd.readLongLittleEndian(z)
                }
            }
        }

        entries += Entry(
            name = String(cd, p + CDH_MIN_LEN, nameLen, Charsets.UTF_8),
            method = cd.readUShortLittleEndian(p + 10),
            compressedSize = compressedSize,
            localHeaderOffset = localHeaderOffset,
        )
        p += CDH_MIN_LEN + nameLen + extraLen + commentLen
    }
    return entries
}

/** Returns the byte offset of the ZIP64 extra field's data within [cd], or -1 if not present. */
private fun zip64ExtraOffset(cd: ByteArray, extraStart: Int, extraLen: Int): Int {
    var q = extraStart
    val end = extraStart + extraLen
    while (q + 4 <= end) {
        val id = cd.readUShortLittleEndian(q)
        val size = cd.readUShortLittleEndian(q + 2)
        if (id == ZIP64_EXTRA_ID) return q + 4
        q += 4 + size
    }
    return -1
}

/**
 * Fetches and parses an archive's central directory, resolving every entry offset to an absolute file position.
 *
 * Reads the tail with a plain `bytes=start-end` request derived from [totalSize]. Use this when the size is
 * known up front and the host does not serve `bytes=-N` suffix ranges.
 *
 * See also [zipDirectory], which gets the size and tail in a single request.
 *
 * @param totalSize the total size of the resource, in bytes
 * @param fetch returns the bytes of a given inclusive byte range; read fully and closed by this function
 * @return the parsed [ZipDirectory]
 * @throws IllegalStateException if the archive has no valid EOCD record
 */
fun readZipDirectory(totalSize: Long, fetch: (LongRange) -> BufferedSource): ZipDirectory {
    val tailStart = maxOf(0L, totalSize - MAX_EOCD_SEARCH)
    val tail = fetch(tailStart..<totalSize).use { it.readByteArray() }
    return readZipDirectory(tail, totalSize, fetch)
}

/**
 * Parses the central directory from an already-fetched [tail], the final bytes of the archive ending
 * exactly at its end. Useful with a `bytes=-N` suffix range, which returns the size and tail together
 * in one request; [fetch] is then called only when the directory falls outside [tail].
 *
 * @param tail the final bytes of the archive
 * @param totalSize the resource's total size
 * @param fetch returns the bytes of an inclusive byte range; read fully and closed by this function
 * @return the parsed [ZipDirectory]
 * @throws IllegalStateException if [tail] holds no valid EOCD record
 */
fun readZipDirectory(tail: ByteArray, totalSize: Long, fetch: (LongRange) -> BufferedSource): ZipDirectory {
    val tailStart = totalSize - tail.size
    val eocd = findEocd(tail)

    val zipStart = tailStart + eocd.recordOffset - eocd.cdSize - eocd.cdOffset
    val cdWithinTail = eocd.recordOffset - eocd.cdSize
    val cd = if (cdWithinTail >= 0) {
        val from = cdWithinTail.toInt()
        tail.copyOfRange(from, from + eocd.cdSize.toInt())
    } else {
        val cdStart = zipStart + eocd.cdOffset
        fetch(cdStart..<cdStart + eocd.cdSize).use { it.readByteArray() }
    }

    val entries = parseCentralDirectory(cd).let { list ->
        if (zipStart == 0L) list else list.map { Entry(it.name, it.method, it.compressedSize, zipStart + it.localHeaderOffset) }
    }
    return ZipDirectory(entries, zipStart + eocd.cdOffset)
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
 * @throws IllegalArgumentException if [method] is unsupported or the local header signature is missing
 */
fun readEntry(
    source: BufferedSource,
    compressedSize: Long,
    method: Int,
    decode: (BufferedSource) -> Source = { it },
): Source {
    require(method == METHOD_STORED || method == METHOD_DEFLATE) { "Unsupported ZIP method: $method" }
    skipLocalHeader(source)
    val payload = decode(source.fixedLength(compressedSize).buffer())
    return if (method == METHOD_DEFLATE) InflaterSource(payload, Inflater(true)) else payload
}

/**
 * Range-fetches and reads an [entry] already in hand; a convenience over [readEntry].
 * The result is lazy and closing it closes the fetched source.
 *
 * @param entry the entry to read, with an absolute offset
 * @param decode transform applied to the raw payload before inflation; defaults to identity
 * @param fetch returns the bytes of the entry's [Entry.dataRange]
 * @return a [Source] yielding the entry's decompressed bytes
 */
fun readZipEntry(
    entry: Entry,
    decode: (BufferedSource) -> Source = { it },
    fetch: (LongRange) -> BufferedSource,
): Source = readEntry(fetch(entry.dataRange), entry.compressedSize, entry.method, decode)

private fun skipLocalHeader(source: BufferedSource) {
    val header = source.readByteArray(LFH_MIN_LEN.toLong())
    require(header.readUIntLittleEndian(0) == LFH_SIG) { "Not a local file header" }
    source.skip((header.readUShortLittleEndian(26) + header.readUShortLittleEndian(28)).toLong())
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

/**
 * Fetches and parses a remote ZIP's central directory over HTTP range requests.
 *
 * A single `bytes=-N` suffix request yields both the total size from Content-Range and the trailing
 * bytes, so the directory is read in one request unless it lies outside that tail.
 *
 * @param url the archive URL
 * @param headers headers to send with every request
 * @return the parsed [ZipDirectory], with absolute offsets
 * @throws IOException if the total size cannot be read from the Content-Range
 * @throws IllegalStateException if the archive has no valid EOCD record
 */
fun OkHttpClient.zipDirectory(url: String, headers: Headers): ZipDirectory {
    val response = newCall(GET(url, headers).newBuilder().header("Range", "bytes=-$MAX_EOCD_SEARCH").build()).execute()
    val total = response.header("Content-Range")?.substringAfterLast("/")?.toLongOrNull() ?: throw IOException("Missing or invalid Content-Range")
    return readZipDirectory(response.body.bytes(), total) { rangeSource(url, headers, it) }
}

/**
 * Reads one [entry] from [url] over a range request.
 * The result is lazy and closing it closes the underlying response.
 *
 * @param url the archive URL
 * @param entry the entry to read, from [zipDirectory]
 * @param headers headers to send
 * @param decode transform applied to the raw payload before inflation; identity by default
 * @return a [Source] yielding the entry's decompressed bytes
 */
fun OkHttpClient.readZipEntry(
    url: String,
    entry: Entry,
    headers: Headers,
    decode: (BufferedSource) -> Source = { it },
): Source = readZipEntry(entry, decode) { rangeSource(url, headers, it) }

private fun OkHttpClient.rangeSource(url: String, headers: Headers, range: LongRange): BufferedSource = newCall(GET(url, headers).newBuilder().range(range).build()).execute().body.source()
