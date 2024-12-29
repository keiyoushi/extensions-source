package eu.kanade.tachiyomi.extension.pt.sussyscan

import android.annotation.SuppressLint
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.concurrent.TimeUnit

class SussyScan : HttpSource() {

    override val name = "Sussy Scan"

    override val baseUrl = "https://new.sussytoons.site"

    private val apiUrl = "https://api-dev.sussytoons.site"

    override val lang = "pt-BR"

    override val supportsLatest = true

    // Moved from Madara
    override val versionId = 2

    private val json: Json by injectLazy()

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(1, 2, TimeUnit.SECONDS)
        .addInterceptor(::imageLocation)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .set("scan-id", "1") // Required header for requests

    // ============================= Popular ==================================

    override fun popularMangaRequest(page: Int): Request {
        return GET("$apiUrl/obras/top5", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val dto = response.parseAs<WrapperDto<List<MangaDto>>>()
        val mangas = dto.results.map { it.toSManga() }
        return MangasPage(mangas, false) // There's a pagination bug
    }

    // ============================= Latest ===================================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$apiUrl/obras/novos-capitulos".toHttpUrl().newBuilder()
            .addQueryParameter("pagina", page.toString())
            .addQueryParameter("limite", "24")
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val dto = response.parseAs<WrapperDto<List<MangaDto>>>()
        val mangas = dto.results.map { it.toSManga() }
        return MangasPage(mangas, dto.hasNextPage())
    }

    // ============================= Search ===================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiUrl/obras".toHttpUrl().newBuilder()
            .addQueryParameter("pagina", page.toString())
            .addQueryParameter("limite", "8")
            .addQueryParameter("obr_nome", query)
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response) = latestUpdatesParse(response)

    // ============================= Details ==================================

    override fun getMangaUrl(manga: SManga) = "$baseUrl${manga.url}"

    override fun mangaDetailsRequest(manga: SManga): Request {
        val url = "$apiUrl/obras".toHttpUrl().newBuilder()
            .addPathSegment(manga.id)
            .build()
        return GET(url, headers)
    }

    override fun mangaDetailsParse(response: Response) =
        response.parseAs<WrapperDto<MangaDto>>().results.toSManga()

    private val SManga.id: String get() {
        val mangaUrl = apiUrl.toHttpUrl().newBuilder()
            .addPathSegments(url)
            .build()
        return mangaUrl.pathSegments[2]
    }

    // ============================= Chapters =================================

    override fun getChapterUrl(chapter: SChapter): String {
        return "$baseUrl/capitulo".toHttpUrl().newBuilder()
            .addPathSegment(chapter.id)
            .build()
            .toString()
    }

    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        return response.parseAs<WrapperDto<WrapperChapterDto>>().results.chapters.map {
            SChapter.create().apply {
                name = it.name
                it.chapterNumber?.let {
                    chapter_number = it
                }
                val chapterApiUrl = "$apiUrl/capitulos".toHttpUrl().newBuilder()
                    .addPathSegment(it.id.toString())
                    .build()
                setUrlWithoutDomain(chapterApiUrl.toString())
                date_upload = it.updateAt.toDate()
            }
        }
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return super.fetchChapterList(manga)
            .map { it.sortedBy(SChapter::chapter_number).reversed() }
    }

    private val SChapter.id: String get() {
        val chapterApiUrl = apiUrl.toHttpUrl().newBuilder()
            .addPathSegments(url)
            .build()
        return chapterApiUrl.pathSegments.last()
    }

    // ============================= Pages ====================================

    override fun pageListRequest(chapter: SChapter) = GET("$apiUrl${chapter.url}", headers)

    override fun pageListParse(response: Response): List<Page> {
        val dto = response.parseAs<WrapperDto<ChapterPageDto>>().results
        return dto.pages.mapIndexed { index, image ->
            val imageUrl = CDN_URL.toHttpUrl().newBuilder()
                .addPathSegments("wp-content/uploads/WP-manga/data")
                .addPathSegments(image.src)
                .build().toString()
            Page(index, imageUrl = imageUrl)
        }
    }

    override fun imageUrlParse(response: Response): String = ""

    override fun imageUrlRequest(page: Page): Request {
        val imageHeaders = headers.newBuilder()
            .add("Referer", "$baseUrl/")
            .build()
        return GET(page.url, imageHeaders)
    }

    // ============================= Utilities ====================================

    private fun MangaDto.toSManga(): SManga {
        val sManga = SManga.create().apply {
            title = name
            thumbnail_url = thumbnail
            initialized = true
            val mangaUrl = "$baseUrl/obra".toHttpUrl().newBuilder()
                .addPathSegment(this@toSManga.id.toString())
                .addPathSegment(this@toSManga.slug)
                .build()
            setUrlWithoutDomain(mangaUrl.toString())
        }

        Jsoup.parseBodyFragment(description).let { sManga.description = it.text() }
        sManga.status = status.toStatus()

        return sManga
    }

    private fun imageLocation(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (response.isSuccessful) {
            return response
        }

        val url = request.url.toString()
        if (url.contains(CDN_URL, ignoreCase = true)) {
            response.close()

            val newRequest = request.newBuilder()
                .url(url.replace(CDN_URL, OLDI_URL, ignoreCase = true))
                .build()

            return chain.proceed(newRequest)
        }
        return response
    }

    private inline fun <reified T> Response.parseAs(): T {
        return json.decodeFromStream(body.byteStream())
    }

    private fun String.toDate() =
        try { dateFormat.parse(this)!!.time } catch (_: Exception) { 0L }

    companion object {
        const val CDN_URL = "https://usc1.contabostorage.com/23b45111d96c42c18a678c1d8cba7123:cdn"
        const val OLDI_URL = "https://oldi.sussytoons.site"

        @SuppressLint("SimpleDateFormat")
        val dateFormat = SimpleDateFormat("yyyy-MM-dd")
    }
}
