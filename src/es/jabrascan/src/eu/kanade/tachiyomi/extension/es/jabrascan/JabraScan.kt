package eu.kanade.tachiyomi.extension.es.jabrascan

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
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.applicationContext
import keiyoushi.utils.md5
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.CacheControl
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import java.io.File
import java.io.IOException
import kotlin.time.Duration.Companion.hours

@Source
abstract class JabraScan : KeiSource() {

    private val pdfCache by lazy { File(applicationContext.cacheDir, "jabrascan_pdf").apply { mkdirs() } }

    private val pdfLock = Mutex()

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor(::renderPdfPage)

    // The site lists its works by their latest chapter, and has no other order
    override val supportsLatest = false

    override suspend fun getPopularManga(page: Int) = MangasPage(getWorks().map { it.toSManga() }, false)

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val works = getWorks().filter { it.matches(query.trim()) }
        return MangasPage(works.map { it.toSManga() }, false)
    }

    private suspend fun getWorks() = client.get("$baseUrl/obras/carrousel", Fetch.XHR).parseAs<List<WorkDto>>()
        .filter { it.isVisible }
        .sortedByDescending { it.lastChapterDate }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val id = url.fragment?.substringBefore('/')?.takeIf { it.isNotEmpty() } ?: return null
        return getWorks().firstOrNull { it.id == id }?.toSManga()
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/#${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String {
        val (workId, number) = chapter.url.split('/', limit = 3)
        return "$baseUrl/#$workId/Chapter$number"
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = async { if (fetchDetails) getWorks().first { it.id == manga.url }.toSManga() else manga }
        val chapterList = async { if (fetchChapters) getChapters(manga.url) else chapters }
        SMangaUpdate(details.await(), chapterList.await())
    }

    // Like the site: the chapters out by today, newest first, and the PDFs only, as the .txt chapters are text
    private suspend fun getChapters(workId: String): List<SChapter> {
        val path = client.get("$baseUrl/capitulos.json", Fetch.XHR).parseAs<Map<String, String>>()[workId]
            ?: return emptyList()
        val now = System.currentTimeMillis()
        return client.get("$baseUrl/$path", Fetch.XHR).parseAs<Map<String, List<ChapterDto>>>()[workId].orEmpty()
            .filter { it.isPdf && it.uploadDate <= now }
            .sortedWith(compareByDescending<ChapterDto> { it.uploadDate }.thenByDescending { it.number.toFloatOrNull() })
            .map { it.toSChapter(workId) }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val (workId, number, file) = chapter.url.split('/', limit = 3)
        // As the site's reader: chapters past 999 are in a folder of their own, the work's id followed by the thousands
        val folder = workId + if ((number.toDoubleOrNull() ?: 0.0) > 999) number.dropLast(3) else ""
        val fileUrl = "https://$BOOKS_HOST".toHttpUrl().newBuilder().addPathSegment(folder).addPathSegment(file).build()
        val pageCount = pdfFile(fileUrl).openPdf { it.pageCount }
        // The page number goes in the fragment, which is never sent: renderPdfPage draws that page of the file
        return List(pageCount) { Page(it, imageUrl = fileUrl.newBuilder().fragment(it.toString()).build().toString()) }
    }

    // PDFs stay in the app's cache until an hour after their last use
    private fun cachedPdf(url: HttpUrl) = File(pdfCache, "${url.toString().md5().toHexString()}.pdf")

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

        val part = File(pdfCache, "${file.name}.part")
        client.get(url, Fetch.XHR, NO_STORE).use { response ->
            part.outputStream().use { output -> response.body.byteStream().copyTo(output) }
        }
        part.renameTo(file)
    }

    private fun renderPdfPage(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val index = request.url.fragment?.toIntOrNull()
        if (request.url.host != BOOKS_HOST || index == null) return chain.proceed(request)

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
        const val BOOKS_HOST = "books.jabrascan.net"
        const val MAX_WIDTH = 1500f
        const val MAX_SCALE = 2.5f
        val PDF_LIFETIME = 1.hours
        val NO_STORE = CacheControl.Builder().noStore().build()
        val JPEG = "image/jpeg".toMediaType()
    }
}
