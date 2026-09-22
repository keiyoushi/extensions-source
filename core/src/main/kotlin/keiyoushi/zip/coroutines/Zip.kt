package keiyoushi.zip.coroutines

import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.network.DEFAULT_CACHE_CONTROL
import keiyoushi.network.get
import keiyoushi.zip.Entry
import keiyoushi.zip.MAX_EOCD_SEARCH
import keiyoushi.zip.ZipDirectory
import keiyoushi.zip.dataRange
import keiyoushi.zip.locateCentralDirectory
import keiyoushi.zip.readEntry
import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.OkHttpClient
import okio.BufferedSource
import okio.Source
import java.io.IOException

/**
 * Suspend equivalent of [keiyoushi.zip.readZipDirectory].
 *
 * Parses the central directory from an already-fetched [tail], calling [fetch] only when the
 * directory falls outside it.
 *
 * @param tail the final bytes of the archive
 * @param totalSize the resource's total size
 * @param fetch returns the bytes of an inclusive byte range; read fully and closed by this function
 * @return the parsed [ZipDirectory]
 * @throws IllegalStateException if [tail] holds no valid EOCD record
 */
suspend fun readZipDirectory(
    tail: ByteArray,
    totalSize: Long,
    fetch: suspend (LongRange) -> BufferedSource,
): ZipDirectory {
    val location = locateCentralDirectory(tail, totalSize)
    val cd = location.bytes ?: fetch(location.range).use { it.readByteArray() }
    return location.parse(cd)
}

/**
 * Suspend equivalent of [keiyoushi.zip.zipDirectory]. Fetches and parses a remote ZIP's central
 * directory over HTTP range requests.
 *
 * @param url the archive URL
 * @param headers headers to send with every request
 * @param cacheControl the cache control settings for the requests
 * @return the parsed [ZipDirectory], with absolute offsets
 * @throws IOException if the total size cannot be read from the Content-Range
 * @throws IllegalStateException if the archive has no valid EOCD record
 */
suspend fun OkHttpClient.zipDirectory(
    url: String,
    headers: Headers,
    cacheControl: CacheControl = DEFAULT_CACHE_CONTROL,
): ZipDirectory {
    val response = get(url, headers.newBuilder().set("Range", "bytes=-$MAX_EOCD_SEARCH").build(), cacheControl)
    val total = response.header("Content-Range")?.substringAfterLast("/")?.toLongOrNull() ?: throw IOException("Missing or invalid Content-Range")
    val tail = response.use { it.body.bytes() }
    return readZipDirectory(tail, total) { rangeSource(url, headers, cacheControl, it) }
}

/**
 * Suspend equivalent of [keiyoushi.zip.readZipEntry]. Range-fetches one [entry] from [url].
 * The result is lazy and closing it closes the underlying response.
 *
 * @param url the archive URL
 * @param entry the entry to read, from [zipDirectory]
 * @param headers headers to send
 * @param cacheControl the cache control settings for the request
 * @param decode transform applied to the raw payload before inflation; identity by default
 * @return a [Source] yielding the entry's decompressed bytes
 */
suspend fun OkHttpClient.readZipEntry(
    url: String,
    entry: Entry,
    headers: Headers,
    cacheControl: CacheControl = DEFAULT_CACHE_CONTROL,
    decode: (BufferedSource) -> Source = { it },
): Source = readEntry(rangeSource(url, headers, cacheControl, entry.dataRange), entry.compressedSize, entry.method, decode)

/**
 * Fetches and parses a remote ZIP's central directory, automatically retrieving the headers from
 * the current [HttpSource] context receiver.
 *
 * @param url the archive URL
 * @param cacheControl the cache control settings for the requests
 * @return the parsed [ZipDirectory], with absolute offsets
 */
context(source: HttpSource)
suspend fun OkHttpClient.zipDirectory(
    url: String,
    cacheControl: CacheControl = DEFAULT_CACHE_CONTROL,
): ZipDirectory = zipDirectory(url, source.headers, cacheControl)

/**
 * Range-fetches one [entry] from [url], automatically retrieving the headers from the current
 * [HttpSource] context receiver. The result is lazy and closing it closes the underlying response.
 *
 * @param url the archive URL
 * @param entry the entry to read, from [zipDirectory]
 * @param cacheControl the cache control settings for the request
 * @param decode transform applied to the raw payload before inflation; identity by default
 * @return a [Source] yielding the entry's decompressed bytes
 */
context(source: HttpSource)
suspend fun OkHttpClient.readZipEntry(
    url: String,
    entry: Entry,
    cacheControl: CacheControl = DEFAULT_CACHE_CONTROL,
    decode: (BufferedSource) -> Source = { it },
): Source = readZipEntry(url, entry, source.headers, cacheControl, decode)

private suspend fun OkHttpClient.rangeSource(
    url: String,
    headers: Headers,
    cacheControl: CacheControl,
    range: LongRange,
): BufferedSource = get(url, headers.newBuilder().set("Range", "bytes=${range.first}-${range.last}").build(), cacheControl).body.source()
