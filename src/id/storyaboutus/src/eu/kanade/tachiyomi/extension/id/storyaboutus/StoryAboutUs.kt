package eu.kanade.tachiyomi.extension.id.storyaboutus

import android.graphics.Bitmap
import android.graphics.Canvas
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
import keiyoushi.utils.firstInstance
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
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
import kotlin.time.Duration.Companion.minutes

@Source
abstract class StoryAboutUs : KeiSource() {
    override val supportsLatest: Boolean get() = false

    private val cacheDir by lazy {
        File(applicationContext.cacheDir, "storyaboutus_pdf").apply { mkdirs() }
    }

    private val pdfPageInterceptor = Interceptor { chain ->
        val request = chain.request()
        val url = request.url
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != PDF_PAGE_SEGMENT) {
            return@Interceptor chain.proceed(request)
        }
        val file = File(cacheDir, url.pathSegments[1])
        if (!file.exists()) throw IOException("PDF is no longer cached, reopen the chapter")
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                renderer.openPage(url.pathSegments[2].toInt()).use { page ->
                    val scale = minOf(MAX_SCALE, MAX_WIDTH / page.width)
                    val bitmap = Bitmap.createBitmap((page.width * scale).toInt(), (page.height * scale).toInt(), Bitmap.Config.ARGB_8888)
                    Canvas(bitmap).drawColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    val buffer = Buffer()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, buffer.outputStream())
                    bitmap.recycle()
                    Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(buffer.asResponseBody("image/jpeg".toMediaType()))
                        .build()
                }
            }
        }
    }

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor(pdfPageInterceptor)

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(StatusFilter()))

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val status = filters.firstInstance<StatusFilter>().toUriPart()
        val mangas = client.get("$baseUrl/api/manga", Fetch.XHR).parseAs<List<MangaDto>>()
            .filter { it.title.contains(query.trim(), ignoreCase = true) }
            .filter { status.isEmpty() || it.status == status }
            .map { it.toSManga() }
        return MangasPage(mangas, false)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val id = url.queryParameter("title") ?: return null
        return fetchManga(id).takeIf { it.chapters.isNotEmpty() }?.toSManga()?.apply { initialized = true }
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/chapter.html?title=${manga.url}"

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/chapter.html?title=${chapter.url.substringAfter('/').substringBefore('/')}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val dto = fetchManga(manga.url)
        return SMangaUpdate(dto.toSManga(), dto.chapters.sortedByDescending { it.number }.map { it.toSChapter() })
    }

    private suspend fun fetchManga(id: String) = client.get("$baseUrl/api/manga/$id", Fetch.XHR).parseAs<MangaDto>()

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val pdfClient = client.newBuilder()
            .connectTimeout(1.minutes)
            .readTimeout(3.minutes)
            .build()
        val pdfCache = CacheControl.Builder().noStore().build()
        val file = pdfClient.get("$CDN_URL/${chapter.url}", Fetch.XHR, pdfCache).use { response ->
            val expiredBefore = System.currentTimeMillis() - 1.hours.inWholeMilliseconds
            cacheDir.listFiles()?.filter { it.lastModified() < expiredBefore }?.forEach { it.delete() }
            File(cacheDir, "chapter_${System.currentTimeMillis()}.pdf").also { target ->
                response.body.byteStream().use { input -> target.outputStream().use { input.copyTo(it) } }
            }
        }
        val pageCount = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { it.pageCount }
        }
        return List(pageCount) { index -> Page(index, imageUrl = "$baseUrl/$PDF_PAGE_SEGMENT/${file.name}/$index") }
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(StatusFilter())

    private companion object {
        const val CDN_URL = "https://cdn.storyabout-us.com"
        const val PDF_PAGE_SEGMENT = "pdf-page"
        const val MAX_SCALE = 2.5f
        const val MAX_WIDTH = 1500f
    }
}
