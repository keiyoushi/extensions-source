package keiyoushi.lib.clipstudioreader

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.network.get
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser

/**
 * Fetches the page list of a loaded `Clip Studio Reader` viewer page.
 *
 * @param document The loaded viewer page.
 * @param headers The [Headers] to use for requests.
 * @return The pages, or an empty list if an EPUB has no images.
 */
suspend fun OkHttpClient.fetchPages(document: Document, headers: Headers): List<Page> {
    val viewerUrl = document.location().toHttpUrl()
    val meta = document.selectFirst("div#meta")
    // EPUBs and the vertical (webtoon) viewer keep their parameters in the URL only
    if (meta == null || viewerUrl.queryParameter("c") != null) return fetchPages(viewerUrl, headers)

    return fetchComicPages(
        cgi = meta.selectFirst("input[name=cgi]")!!.absUrl("value").toHttpUrl(),
        param = meta.selectFirst("input[name=param]")!!.attr("value"),
        trial = meta.selectFirst("input[name=trial]")?.attr("value"),
        headers = headers,
    )
}

/**
 * Fetches the page list of a loaded `Clip Studio Reader` viewer page, automatically retrieving the
 * headers from the current [HttpSource] context receiver.
 *
 * @see fetchPages
 */
context(source: HttpSource)
suspend fun OkHttpClient.fetchPages(document: Document): List<Page> = fetchPages(document, source.headers)

/**
 * Fetches the page list straight from a `Clip Studio Reader` viewer URL that carries its parameters,
 * `cgi` and `param` (and `trial`) for comics or `c` (and `t`) for EPUBs.
 *
 * @param viewerUrl The viewer URL.
 * @param headers The [Headers] to use for requests.
 * @return The pages, or an empty list if an EPUB has no images.
 */
suspend fun OkHttpClient.fetchPages(viewerUrl: HttpUrl, headers: Headers): List<Page> {
    if (viewerUrl.queryParameter("c") != null) return fetchEpubPages(viewerUrl, headers)

    return fetchComicPages(
        cgi = viewerUrl.resolve(viewerUrl.queryParameter("cgi")!!)!!,
        param = viewerUrl.queryParameter("param")!!.replace(' ', '+'),
        trial = viewerUrl.queryParameter("trial"),
        headers = headers,
    )
}

/**
 * Fetches the page list straight from a `Clip Studio Reader` viewer URL, automatically retrieving the
 * headers from the current [HttpSource] context receiver.
 *
 * @see fetchPages
 */
context(source: HttpSource)
suspend fun OkHttpClient.fetchPages(viewerUrl: HttpUrl): List<Page> = fetchPages(viewerUrl, source.headers)

private suspend fun OkHttpClient.fetchComicPages(cgi: HttpUrl, param: String, trial: String?, headers: Headers): List<Page> {
    val timeKeyUrl = cgi.newBuilder()
        .addQueryParameter("mode", MODE_TIME_KEY)
        .addQueryParameter("reqtype", "1")
        .addQueryParameter("vm", VIEWER_MODE)
        .addQueryParameter("param", param)
        .build()

    val timeKey = get(timeKeyUrl, headers).asJsoup(Parser.xmlParser())
    if (timeKey.selectFirst("Code")?.text() != "1000") throw Exception("Viewer error: ${timeKey.text()}")
    val key = timeKey.selectFirst("Content")!!.text()

    val face = get(cgi.fileUrl(MODE_FACE_XML, "face.xml", key), headers).asJsoup(Parser.xmlParser())
    val totalPages = face.selectFirst("TotalPage")!!.text().toInt()
    val gridWidth = face.selectFirst("Scramble > Width")!!.text()
    val gridHeight = face.selectFirst("Scramble > Height")!!.text()

    val pageNumbers = trial?.split('_')?.takeIf { it.size == 2 }?.let { (first, last) -> first.toInt()..last.toInt() }
        ?: 0..<totalPages

    return pageNumbers.mapIndexed { i, pageNumber ->
        val pageUrl = cgi.fileUrl(MODE_PAGE_XML, pageNumber.toString().padStart(4, '0') + ".xml", key).newBuilder()
            .fragment("$PAGE_FRAGMENT,$gridWidth,$gridHeight")
            .build()
        Page(i, imageUrl = pageUrl.toString())
    }
}

private suspend fun OkHttpClient.fetchEpubPages(viewerUrl: HttpUrl, headers: Headers): List<Page> = coroutineScope {
    val contentId = viewerUrl.queryParameter("c")!!

    val configUrl = viewerUrl.resolve("${viewerUrl.queryParameter("s") ?: "default"}.jsonc")!!
    val apiUrl = viewerUrl.resolve(get(configUrl, headers).parseAs<ViewerConfig>().api)!!

    val tokenUrl = apiUrl.newBuilder()
        .addPathSegments("tokens/viewer")
        .addQueryParameter("content_id", contentId)
        .build()

    val tokenHeaders = viewerUrl.queryParameter("t")?.let { headers.withBearer(it) } ?: headers
    val token = get(tokenUrl, tokenHeaders).parseAs<TokenResponse>().token
    val apiHeaders = headers.withBearer(token)

    val metaUrl = apiUrl.newBuilder().addPathSegments("contents/$contentId/meta").build()
    val contentUrl = get(metaUrl, apiHeaders).parseAs<MetaResponse>().content.baseUrl.toHttpUrl()

    val preprocessUrl = contentUrl.newBuilder().addPathSegment("preprocess-settings.json").build()
    val imageKey = async { get(preprocessUrl, apiHeaders).parseAs<PreprocessSettings>().imageKey }

    val containerUrl = contentUrl.newBuilder().addPathSegments("META-INF/container.xml").build()
    val container = get(containerUrl, apiHeaders).asJsoup(Parser.xmlParser())
    val opfUrl = contentUrl.newBuilder().addPathSegments(container.selectFirst("*|rootfile")!!.attr("full-path")).build()
    val opf = get(opfUrl, apiHeaders).asJsoup(Parser.xmlParser())

    val fragment = "$EPUB_FRAGMENT,${imageKey.await() ?: ""},$token"
    opf.select("*|item[media-type^=image/]").mapIndexed { i, item ->
        val imageUrl = opfUrl.resolve(item.attr("href"))!!.newBuilder()
            .fragment(fragment)
            .build()
        Page(i, imageUrl = imageUrl.toString())
    }
}

internal fun HttpUrl.fileUrl(mode: String, file: String, key: String): HttpUrl = newBuilder()
    .addQueryParameter("mode", mode)
    .addQueryParameter("reqtype", "0")
    .addQueryParameter("vm", VIEWER_MODE)
    .addQueryParameter("file", file)
    .addQueryParameter("param", key)
    .build()

private fun Headers.withBearer(token: String) = newBuilder().set("Authorization", "Bearer $token").build()

private const val MODE_FACE_XML = "7"
private const val MODE_PAGE_XML = "8"
private const val MODE_TIME_KEY = "999"
private const val VIEWER_MODE = "4"

internal const val PAGE_FRAGMENT = "csr-page"
internal const val EPUB_FRAGMENT = "csr-epub"
