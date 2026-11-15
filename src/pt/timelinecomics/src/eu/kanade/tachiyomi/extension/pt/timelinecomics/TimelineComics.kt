package eu.kanade.tachiyomi.extension.pt.timelinecomics

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.applicationContext
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import okhttp3.CacheControl
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.File
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

@Source
abstract class TimelineComics : KeiSource() {

    private val pdfCache by lazy { File(applicationContext.cacheDir, "timelinecomics_pdf").apply { mkdirs() } }

    private val pdfLock = Mutex()

    // No time limit for the whole call since some files are over 100 MB (the read timeout still stops a stalled download)
    private val pdfClient by lazy { client.newBuilder().callTimeout(Duration.ZERO).build() }

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor(::renderPdfPage)

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get(baseUrl, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select(".PopularPosts li").map { post ->
            SManga.create().apply {
                val link = post.selectFirst(".item-title a")!!
                setUrlWithoutDomain(link.absUrl("href"))
                title = link.text()
                thumbnail_url = post.selectFirst(".item-thumbnail img")?.absUrl("src")?.let(::coverUrl)
            }
        }
        return MangasPage(mangas, false)
    }

    override suspend fun getLatestUpdates(page: Int) = getSearchMangaList(page, "", FilterList())

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val label = filters.firstInstanceOrNull<LabelFilter>()?.state.orEmpty().trim()
        val search = if (label.isEmpty()) query.trim() else "${query.trim()} label:\"$label\"".trim()
        val url = "$baseUrl/feeds/posts/summary".toHttpUrl().newBuilder()
            .addQueryParameter("alt", "json")
            .apply { if (search.isNotEmpty()) addQueryParameter("q", search) }
            .addQueryParameter("start-index", ((page - 1) * PAGE_SIZE + 1).toString())
            .addQueryParameter("max-results", PAGE_SIZE.toString())
            .build()
        val posts = client.get(url, Fetch.XHR).parseAs<FeedResponseDto>().feed.entry
        return MangasPage(posts.map { it.toSManga() }, posts.size == PAGE_SIZE)
    }

    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        if (url.host != baseUrl.toHttpUrl().host || !url.encodedPath.startsWith("/search/label/")) {
            return super.getMangasByUrl(url, page)
        }
        val label = LabelFilter().apply { state = url.pathSegments[2] }
        return getSearchMangaList(page, "", FilterList(label))
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val isPost = url.pathSegments.size == 3 && url.encodedPath.endsWith(".html")
        if (url.host != baseUrl.toHttpUrl().host || !isPost) return null
        val document = client.get(baseUrl + url.encodedPath, Fetch.DOCUMENT).asJsoup()
        return document.toSManga().apply { this.url = url.encodedPath }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        return SMangaUpdate(document.toSManga().apply { url = manga.url }, document.toSChapters())
    }

    private fun Document.toSManga() = SManga.create().apply {
        title = selectFirst(".post-title")!!.text()
        thumbnail_url = selectFirst("meta[property=og:image]")?.attr("content")?.let(::coverUrl)
        val labels = select(".post-labels a").map { it.text() }
        genre = labels.joinToString().ifEmpty { null }
        status = if ("Completos" in labels) SManga.COMPLETED else SManga.UNKNOWN
    }

    // One chapter per Drive file, as several links can lead to the same file, the post's last file first
    private fun Document.toSChapters() = select(".post-body a[href]")
        .mapNotNull { link -> link.driveFile()?.let { it to link } }
        .groupBy({ it.first }, { it.second })
        .mapNotNull { (file, links) ->
            val name = links.firstNotNullOfOrNull { it.chapterName() } ?: return@mapNotNull null
            SChapter.create().apply {
                url = file
                this.name = name
            }
        }
        .reversed()

    // Links are drive.google.com/open?id=<id> or drive.google.com/file/d/<id>/view, with a resource key for some
    private fun Element.driveFile(): String? {
        val url = absUrl("href").toHttpUrlOrNull()?.takeIf { it.host == "drive.google.com" } ?: return null
        val id = url.queryParameter("id")
            ?: url.pathSegments.getOrNull(2)?.takeIf { url.pathSegments[1] == "d" }
            ?: return null
        val resourceKey = url.queryParameter("resourcekey")?.let { "&resourcekey=$it" }.orEmpty()
        return "/open?id=$id$resourceKey"
    }

    // The caption under a cover can hold more than its link, like "Green Lantern" above a link "024"
    private fun Element.chapterName(): String? {
        val cell = closest("td")?.takeIf { td -> td.select("a[href]").mapNotNull { it.driveFile() }.distinct().size == 1 }
        return (cell ?: this).text().ifEmpty { null }
    }

    override fun getChapterUrl(chapter: SChapter) = "https://drive.google.com${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val fileUrl = getChapterUrl(chapter).toHttpUrl().newBuilder()
            .host(DOWNLOAD_HOST)
            .encodedPath("/download")
            .addQueryParameter("export", "download")
            .build()
        val pageCount = pdfFile(fileUrl).openPdf { it.pageCount }
        // The page number goes in the fragment, which is never sent: renderPdfPage draws that page of the file
        return List(pageCount) { Page(it, imageUrl = fileUrl.newBuilder().fragment(it.toString()).build().toString()) }
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("Nome de um marcador do blog, como Batman ou 18+"),
        LabelFilter(),
    )

    // PDFs stay in the app's cache until an hour after their last use
    private fun cachedPdf(url: HttpUrl) = File(pdfCache, "${url.queryParameter("id")}.pdf")

    private suspend fun pdfFile(url: HttpUrl): File {
        val file = cachedPdf(url)
        if (!file.exists()) {
            pdfLock.withLock {
                if (!file.exists()) download(url, file)
            }
        }
        file.setLastModified(System.currentTimeMillis())
        return file
    }

    private suspend fun download(url: HttpUrl, file: File) {
        val expired = System.currentTimeMillis() - PDF_LIFETIME.inWholeMilliseconds
        pdfCache.listFiles()?.filter { it.lastModified() < expired }?.forEach { it.delete() }

        var response = pdfClient.get(url, Fetch.DOCUMENT, NO_STORE, ensureSuccess = false)
        // Files that aren't public lead to a sign-in page, deleted ones to a 404
        if (response.request.url.host == SIGN_IN_HOST || response.code == 404) {
            response.close()
            throw IOException("This file isn't available on Google Drive")
        }
        // Drive answers with a page for errors, like an exceeded download quota, and for files too big to scan for
        // viruses (over 100 MB), whose form downloads them anyway when it is sent from that page
        if (response.isHtml()) {
            val page = response.asJsoup()
            val form = page.selectFirst("form#download-form") ?: throw IOException(page.driveError())
            val formUrl = form.absUrl("action").toHttpUrl().newBuilder().apply {
                form.select("input[type=hidden]").forEach { addQueryParameter(it.attr("name"), it.attr("value")) }
            }.build()
            response = pdfClient.get(formUrl, Fetch.DOCUMENT.from(page.location()), NO_STORE, ensureSuccess = false)
            if (response.isHtml()) throw IOException(response.asJsoup().driveError())
        }
        val part = File(pdfCache, "${file.name}.part")
        response.use { part.outputStream().use { output -> it.body.byteStream().copyTo(output) } }
        part.renameTo(file)
    }

    private fun Response.isHtml() = body.contentType()?.subtype == "html"

    private fun Document.driveError() = selectFirst(".uc-error-subcaption")?.text() ?: title()

    private fun renderPdfPage(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val index = request.url.fragment?.toIntOrNull()
        if (request.url.host != DOWNLOAD_HOST || index == null) return chain.proceed(request)

        // getPageList downloaded the file: it's only gone if the chapter stayed open past the cache's lifetime
        val pdf = cachedPdf(request.url.newBuilder().fragment(null).build()).takeIf { it.exists() }
            ?: throw IOException("Open the chapter again to download it")
        pdf.setLastModified(System.currentTimeMillis())
        val image = Buffer()
        pdf.openPdf { renderer ->
            renderer.openPage(index).use { page ->
                val scale = minOf(MAX_SCALE, MAX_WIDTH / page.width)
                val bitmap = Bitmap.createBitmap((page.width * scale).toInt(), (page.height * scale).toInt(), Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, image.outputStream())
                bitmap.recycle()
            }
        }
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(image.asResponseBody(JPEG))
            .build()
    }

    private fun <T> File.openPdf(block: (PdfRenderer) -> T): T {
        val descriptor = ParcelFileDescriptor.open(this, ParcelFileDescriptor.MODE_READ_ONLY)
        return descriptor.use { PdfRenderer(it).use(block) }
    }

    private companion object {
        const val PAGE_SIZE = 20
        const val DOWNLOAD_HOST = "drive.usercontent.google.com"
        const val SIGN_IN_HOST = "accounts.google.com"
        const val MAX_WIDTH = 1500f
        const val MAX_SCALE = 2.5f
        val PDF_LIFETIME = 1.hours
        val NO_STORE = CacheControl.Builder().noStore().build()
        val JPEG = "image/jpeg".toMediaType()
    }
}
