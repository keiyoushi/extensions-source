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
        .set("scan-id", "1")

    // ============================= Popular ==================================

    override fun popularMangaRequest(page: Int) = GET("$apiUrl/obras/top5", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val dto = response.parseAs<WrapperDto<List<MangaDto>>>()
        val mangas = dto.results.map(::toSManga)
        return MangasPage(mangas, dto.hasNextPage())
    }

    private fun toSManga(dto: MangaDto) = SManga.create().apply {
        title = dto.name
        thumbnail_url = dto.thumbnail
        Jsoup.parseBodyFragment(dto.description).let { description = it.text() }
        url = "/obras/${dto.id}/${dto.slug}#${dto.scanId}"
        initialized = true
        status = dto.status.toStatus()
    }

    // ============================= Latest ===================================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$apiUrl/obras/novos-capitulos".toHttpUrl().newBuilder()
            .addQueryParameter("pagina", page.toString())
            .addQueryParameter("limite", "24")
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    // ============================= Search ===================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiUrl/obras".toHttpUrl().newBuilder()
            .addQueryParameter("pagina", page.toString())
            .addQueryParameter("limite", "8")
            .addQueryParameter("obr_nome", query)
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    // ============================= Details ==================================

    override fun getMangaUrl(manga: SManga) = "$baseUrl${manga.url}"
        .replace("obras", "obra")

    override fun mangaDetailsRequest(manga: SManga) =
        GET("$apiUrl${manga.url.substringBeforeLast("/")}", headers)

    override fun mangaDetailsParse(response: Response) =
        toSManga(response.parseAs<WrapperDto<MangaDto>>().results)

    // ============================= Chapters =================================

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl${chapter.url}"
        .replace("capitulos", "capitulo")

    override fun chapterListRequest(manga: SManga) = throw UnsupportedOperationException()

    private fun chapterListRequest(page: Int, manga: SManga): Request {
        val scanId = manga.url.substringAfter("#")
        val url = "$apiUrl/obras/${manga.getId()}/capitulos".toHttpUrl().newBuilder()
            .addQueryParameter("pagina", page.toString())
            .addQueryParameter("limite", "100")
            .build()

        return GET(url, headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        return response.parseAs<WrapperDto<List<ChapterDto>>>().results.map {
            SChapter.create().apply {
                name = it.name
                it.chapterNumber?.let {
                    chapter_number = it
                }
                url = "/capitulos/${it.id}"
                date_upload = it.updateAt.toDate()
            }
        }
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val chapterList = mutableListOf<SChapter>()
        var page = 1
        do {
            val chapters = client.newCall(chapterListRequest(page++, manga))
                .execute().let(::chapterListParse)
            chapterList += chapters
        } while (chapters.isNotEmpty())

        return Observable.just(chapterList.reversed())
    }

    private fun SManga.getId(): String {
        val pathSegment = url.split("/")
        return pathSegment[pathSegment.size - 2]
    }

    // ============================= Pages ====================================

    override fun pageListRequest(chapter: SChapter) = GET("$apiUrl${chapter.url}")

    override fun pageListParse(response: Response): List<Page> {
        val dto = response.parseAs<WrapperPageDto>().result
        return dto.pages.mapIndexed { index, image ->
            Page(index, imageUrl = "$CDN_URL/wp-content/uploads/WP-manga/data/${image.src}")
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
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")
    }
}
