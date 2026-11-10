package keiyoushi.zip

import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.network.DEFAULT_CACHE_CONTROL
import keiyoushi.network.Fetch
import keiyoushi.network.get
import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Response
import okio.BufferedSource
import okio.Source
import java.io.IOException
import java.nio.charset.Charset

/**
 * Parses the central directory from an already-fetched [tail], the final bytes of the archive
 * ending exactly at its end, calling [fetch] only when the directory falls outside it.
 *
 * @param tail the final bytes of the archive
 * @param totalSize the resource's total size
 * @param charset the charset of entry names not flagged as UTF-8
 * @param fetch returns the bytes of an inclusive byte range; read fully and closed by this function
 * @return the parsed [ZipDirectory]
 * @throws java.util.zip.ZipException if [tail] holds no valid EOCD record
 */
private suspend fun readZipDirectory(
    tail: ByteArray,
    totalSize: Long,
    charset: Charset,
    fetch: suspend (LongRange) -> BufferedSource,
): ZipDirectory {
    val location = locateCentralDirectory(tail, totalSize)
    val cd = location.bytes ?: fetch(location.range).use { it.readByteArray() }
    return location.parse(cd, charset)
}

/**
 * Fetches and parses a remote ZIP's central directory over HTTP range requests.
 *
 * A single `bytes=-N` suffix request yields both the total size from Content-Range and the trailing
 * bytes, so the directory is read in one request unless it lies outside that tail.
 *
 * @param url the archive URL
 * @param headers headers to send with every request
 * @param cacheControl the cache control settings for the requests
 * @param charset the charset of entry names not flagged as UTF-8, e.g. `Charset.forName("MS932")` for archives made by Japanese tools
 * @return the parsed [ZipDirectory], with absolute offsets
 * @throws IOException if the server ignores the Range header or the total size cannot be read from the Content-Range
 * @throws java.util.zip.ZipException if the archive has no valid EOCD record
 */
suspend fun OkHttpClient.zipDirectory(
    url: String,
    headers: Headers,
    cacheControl: CacheControl = DEFAULT_CACHE_CONTROL,
    charset: Charset = Charsets.UTF_8,
): ZipDirectory {
    val response = get(url, headers.newBuilder().set("Range", "bytes=-$MAX_EOCD_SEARCH").build(), Fetch.NONE, cacheControl)
    val tail = response.rangeSource().use { it.readByteArray() }
    val total = response.header("Content-Range")?.substringAfterLast("/")?.toLongOrNull() ?: throw IOException("Missing or invalid Content-Range")
    return readZipDirectory(tail, total, charset) { rangeSource(url, headers, cacheControl, it) }
}

/**
 * Range-fetches one [entry] from [url].
 * The result is lazy and closing it closes the underlying response.
 *
 * @param url the archive URL
 * @param entry the entry to read, from [zipDirectory]
 * @param headers headers to send
 * @param cacheControl the cache control settings for the request
 * @param decode transform applied to the raw payload before inflation; identity by default
 * @return a [Source] yielding the entry's decompressed bytes
 * @throws IOException if the server ignores the Range header
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
 * @param charset the charset of entry names not flagged as UTF-8
 * @return the parsed [ZipDirectory], with absolute offsets
 */
context(source: HttpSource)
suspend fun OkHttpClient.zipDirectory(
    url: String,
    cacheControl: CacheControl = DEFAULT_CACHE_CONTROL,
    charset: Charset = Charsets.UTF_8,
): ZipDirectory = zipDirectory(url, source.headers, cacheControl, charset)

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

/**
 * The body of this response to a range request. Closing it closes the response.
 *
 * @return the body's source
 * @throws IOException if the server ignored the Range header and sent the whole resource
 */
fun Response.rangeSource(): BufferedSource {
    if (code != HTTP_PARTIAL_CONTENT) {
        close()
        throw IOException("Range request answered with $code, not 206")
    }
    return body.source()
}

private suspend fun OkHttpClient.rangeSource(
    url: String,
    headers: Headers,
    cacheControl: CacheControl,
    range: LongRange,
): BufferedSource = get(url, headers.newBuilder().set("Range", "bytes=${range.first}-${range.last}").build(), Fetch.NONE, cacheControl).rangeSource()

private const val HTTP_PARTIAL_CONTENT = 206
