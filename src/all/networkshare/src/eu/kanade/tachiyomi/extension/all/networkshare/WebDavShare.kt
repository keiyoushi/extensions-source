package eu.kanade.tachiyomi.extension.all.networkshare

import eu.kanade.tachiyomi.network.HttpException
import keiyoushi.network.Fetch
import keiyoushi.network.call
import keiyoushi.network.get
import keiyoushi.utils.asJsoup
import keiyoushi.utils.tryParseZonedDateTime
import keiyoushi.zip.ZipDirectory
import keiyoushi.zip.rangeSource
import keiyoushi.zip.zipDirectory
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSource
import org.jsoup.parser.Parser
import java.io.EOFException
import java.io.IOException
import java.time.format.DateTimeFormatter

class WebDavShare(
    private val client: OkHttpClient,
    private val root: HttpUrl,
    private val headers: Headers,
) : Share {
    override suspend fun list(path: String): List<RemoteFile> {
        val folder = fileUrl(path).newBuilder().addPathSegment("").build()
        val request = Request.Builder()
            .url(folder)
            .headers(headers)
            .header("Depth", "1")
            .method("PROPFIND", PROPFIND_BODY.toRequestBody(XML_MEDIA_TYPE))
            .build()
        val response = client.call(request, ensureSuccess = false)
        if (response.code != 207) {
            response.close()
            throw IOException(
                when (response.code) {
                    401 -> "Wrong username or password"
                    404 -> "Folder not found: ${folder.encodedPath}"
                    else -> "The server answered PROPFIND with HTTP ${response.code}; is this a WebDAV address?"
                },
            )
        }
        val folderSegments = folder.pathSegments.filter { it.isNotEmpty() }
        return response.asJsoup(Parser.xmlParser()).select("*|response").mapNotNull { item ->
            val href = folder.resolve(item.selectFirst("*|href")!!.text())!!
            val segments = href.pathSegments.filter { it.isNotEmpty() }
            // The folder itself; behind a reverse proxy its href can lack the address's leading segments
            if (segments.isEmpty() || folderSegments.takeLast(segments.size) == segments) return@mapNotNull null
            RemoteFile(
                name = segments.last(),
                isDirectory = item.selectFirst("*|resourcetype > *|collection") != null,
                size = item.selectFirst("*|getcontentlength")?.text()?.toLongOrNull() ?: 0L,
                lastModified = LAST_MODIFIED_FORMAT.tryParseZonedDateTime(item.selectFirst("*|getlastmodified")?.text()),
            )
        }
    }

    override suspend fun read(path: String, range: LongRange?): BufferedSource = if (range == null) {
        client.get(fileUrl(path), headers, Fetch.NONE).body.source()
    } else {
        val response = try {
            client.get(fileUrl(path), headers.newBuilder().set("Range", "bytes=${range.first}-${range.last}").build(), Fetch.NONE)
        } catch (e: HttpException) {
            // 416: the file is shorter now than when its offsets were read
            if (e.code == 416) throw EOFException("$path ends before byte ${range.first}")
            throw e
        }
        response.rangeSource()
    }

    override suspend fun zipDirectory(path: String): ZipDirectory = client.zipDirectory(fileUrl(path).toString(), headers)

    override fun url(path: String): String = fileUrl(path).toString()

    private fun fileUrl(path: String): HttpUrl = root.newBuilder().apply {
        path.split('/').filter { it.isNotEmpty() }.forEach { addPathSegment(it) }
    }.build()

    companion object {
        private val XML_MEDIA_TYPE = "application/xml".toMediaType()

        private val LAST_MODIFIED_FORMAT = DateTimeFormatter.RFC_1123_DATE_TIME

        private const val PROPFIND_BODY = """<?xml version="1.0" encoding="utf-8"?><propfind xmlns="DAV:"><prop><resourcetype/><getcontentlength/><getlastmodified/></prop></propfind>"""
    }
}
