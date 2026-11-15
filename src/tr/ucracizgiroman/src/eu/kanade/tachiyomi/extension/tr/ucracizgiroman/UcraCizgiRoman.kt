package eu.kanade.tachiyomi.extension.tr.ucracizgiroman

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.applicationContext
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParseDate
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.File
import java.io.IOException
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.Locale
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

@Source
abstract class UcraCizgiRoman : KeiSource() {

    private val pdfCache by lazy { File(applicationContext.cacheDir, "ucracizgiroman_pdf").apply { mkdirs() } }

    private val pdfLock = Mutex()

    // No time limit for the whole call since some files are large (the read timeout still stops a stalled download)
    private val pdfClient by lazy { client.newBuilder().callTimeout(Duration.ZERO).build() }

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor(::renderPdfPage)

    override suspend fun getPopularManga(page: Int) = getSeries(page, "", "published")

    override suspend fun getLatestUpdates(page: Int) = getSeries(page, "", "updated")

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList) = getSeries(page, query.trim(), "published")

    // Series posts are the ones with a status line, like "DURUM: Tamamlandı"
    private suspend fun getSeries(page: Int, query: String, order: String): MangasPage {
        val url = "$baseUrl/feeds/posts/summary".toHttpUrl().newBuilder()
            .addQueryParameter("alt", "json")
            .addQueryParameter("q", "DURUM $query".trim())
            .addQueryParameter("orderby", order)
            .addQueryParameter("start-index", ((page - 1) * PAGE_SIZE + 1).toString())
            // One more than a page tells whether another one follows: the search feed has no total
            .addQueryParameter("max-results", (PAGE_SIZE + 1).toString())
            .build()
        val posts = client.get(url, Fetch.XHR).parseAs<FeedResponseDto>().feed.entry
        return MangasPage(posts.take(PAGE_SIZE).map { it.toSManga() }, posts.size > PAGE_SIZE)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        // The blog's Blogger address, used by its older links, redirects to the site
        val isSite = url.host == baseUrl.toHttpUrl().host || url.host == BLOGSPOT_HOST
        val isPost = url.pathSegments.size == 3 && url.encodedPath.endsWith(".html")
        if (!isSite || !isPost) return null
        val document = client.get(postPage(url.encodedPath), Fetch.DOCUMENT).asJsoup()
        // An issue's download button leads to its series' post, which lists all the issues
        val seriesUrl = document.select("div.post_body a[href]")
            .firstOrNull { it.text().equals("Sayıyı İndir", ignoreCase = true) }
            ?.absUrl("href")?.toHttpUrlOrNull()
            ?.takeIf { it.host == baseUrl.toHttpUrl().host || it.host == BLOGSPOT_HOST }
            ?.encodedPath
        val (path, post) = when {
            seriesUrl != null -> seriesUrl to client.get(postPage(seriesUrl), Fetch.DOCUMENT).asJsoup()
            document.selectFirst("div.issue") != null || document.isReadable() -> url.encodedPath to document
            // Other posts, like the site's lists of series
            else -> return null
        }
        return post.toSManga().apply {
            this.url = path
            initialized = true
        }
    }

    private fun postPage(path: String) = baseUrl.toHttpUrl().newBuilder().encodedPath(path).build()

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        return SMangaUpdate(document.toSManga().apply { url = manga.url }, document.toSChapters(manga.url))
    }

    private fun Document.toSManga() = SManga.create().apply {
        title = selectFirst("h1.post_title")!!.text()
        thumbnail_url = selectFirst("img#cover")?.absUrl("src")?.let(::coverUrl)
        // Lines like "YAYINEVİ: Marvel", then the summary
        val lines = selectFirst("p#description")?.html()?.split(LINE_BREAK_REGEX)
            ?.map { Jsoup.parseBodyFragment(it).text() }
            .orEmpty()
        val fields = lines.mapNotNull { FIELD_REGEX.matchEntire(it)?.destructured }.associate { (name, value) -> name to value }
        description = MarkdownDescription.Builder()
            .description(lines.filterNot(FIELD_REGEX::matches).joinToString("\n").trim())
            .field("Ana seri", fields["ANA SERİ"])
            .field("Orijinal isim", fields["ORİJİNAL İSİM"])
            .field("Yayınevi", fields["YAYINEVİ"])
            .field("Yayın yılı", fields["YAYIN YILI"])
            .field("Seri", fields["SERİ"])
            .build()
        status = when (fields["DURUM"]?.lowercase(TURKISH)) {
            "devam ediyor" -> SManga.ONGOING
            "tamamlandı" -> SManga.COMPLETED
            "teliflendi" -> SManga.LICENSED
            else -> SManga.UNKNOWN
        }
        if (status == SManga.COMPLETED || selectFirst("div.issue") == null) update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
    }

    // A series post lists its issues, each with a link to the post that reads it; a single issue reads in its own post
    private fun Document.toSChapters(postUrl: String): List<SChapter> {
        if (selectFirst("div.issue") == null) {
            if (!isReadable()) return emptyList()
            return listOf(
                SChapter.create().apply {
                    url = postUrl
                    name = selectFirst("h1.post_title")!!.text()
                    date_upload = postDate(postUrl)
                },
            )
        }
        val issues = select("div.issue li").mapNotNull { issue ->
            val link = issue.select("a[href]").firstOrNull { it.text().equals("Online Oku", ignoreCase = true) } ?: return@mapNotNull null
            SChapter.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                name = issue.selectFirst(".uk-accordion-title")!!.text()
                date_upload = postDate(url)
            }
        }.distinctBy { it.url }
        // The list is in reading order, which the titles' numbers don't always give (crossovers, years in titles)
        issues.forEachIndexed { i, chapter -> chapter.chapter_number = i + 1f }
        return issues.reversed()
    }

    // Post addresses only give their month: /2024/09/<slug>.html
    private fun postDate(url: String) = MONTH_FORMAT.tryParseDate(url.removePrefix("/").take(7), TURKEY)

    private fun Document.isReadable() = selectFirst(DRIVE_FRAME) != null || imagePages().isNotEmpty()

    // A few posts show their pages as images instead of a PDF, outside any link
    private fun Document.imagePages() = select("div.post_body div.separator img").filter { it.closest("a[href]") == null }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        // The post shows its PDF through Drive's viewer: drive.google.com/file/d/<id>/preview
        val frame = document.selectFirst(DRIVE_FRAME)
            ?: return document.imagePages().mapIndexed { i, image -> Page(i, imageUrl = image.absUrl("src")) }
        val fileId = frame.absUrl("src").toHttpUrl().pathSegments[2]
        val fileUrl = "https://$DOWNLOAD_HOST/download".toHttpUrl().newBuilder()
            .addQueryParameter("id", fileId)
            .addQueryParameter("export", "download")
            .build()
        val pageCount = pdfFile(fileUrl).openPdf { it.pageCount }
        // The page number goes in the fragment, which is never sent: renderPdfPage draws that page of the file
        return List(pageCount) { Page(it, imageUrl = fileUrl.newBuilder().fragment(it.toString()).build().toString()) }
    }

    // PDFs stay in the app's cache until an hour after their last use
    private fun cachedPdf(url: HttpUrl) = File(pdfCache, "${url.queryParameter("id")}.pdf")

    private suspend fun pdfFile(url: HttpUrl): File {
        val file = cachedPdf(url)
        if (!file.exists()) {
            pdfLock.withLock {
                if (!file.exists()) download(url, file)
            }
        }
        file.setLastModified(Clock.System.now().toEpochMilliseconds())
        return file
    }

    private suspend fun download(url: HttpUrl, file: File) {
        val expired = (Clock.System.now() - PDF_LIFETIME).toEpochMilliseconds()
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
        pdf.setLastModified(Clock.System.now().toEpochMilliseconds())
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
        const val PAGE_SIZE = 24
        const val BLOGSPOT_HOST = "ucracizgiroman.blogspot.com"
        const val DRIVE_FRAME = "iframe[src*=drive.google.com/file/d/]"
        const val DOWNLOAD_HOST = "drive.usercontent.google.com"
        const val SIGN_IN_HOST = "accounts.google.com"
        const val MAX_WIDTH = 1500f
        const val MAX_SCALE = 2.5f
        val PDF_LIFETIME = 1.hours
        val NO_STORE = CacheControl.Builder().noStore().build()
        val JPEG = "image/jpeg".toMediaType()
        val TURKISH: Locale = Locale.forLanguageTag("tr")
        val TURKEY: ZoneId = ZoneId.of("Europe/Istanbul")
        val MONTH_FORMAT: DateTimeFormatter = DateTimeFormatterBuilder()
            .appendPattern("yyyy/MM")
            .parseDefaulting(ChronoField.DAY_OF_MONTH, 1)
            .toFormatter()
        val LINE_BREAK_REGEX = Regex("""<br\s*/?>""")
        val FIELD_REGEX = Regex("""([A-ZÇĞİÖŞÜ ]+):\s*(.+)""")
    }
}
