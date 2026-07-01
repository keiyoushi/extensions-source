package eu.kanade.tachiyomi.extension.pt.plumacomics

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import kotlin.time.Duration.Companion.seconds

@Source
abstract class PlumaComics : HttpSource() {

    override val supportsLatest: Boolean = true

    override val client = super.client.newBuilder()
        .rateLimit(3, 1.seconds)
        .build()

    private val imgHeaders = headers.newBuilder()
        .add("Referer", "$baseUrl/")
        .build()

    override val versionId = 5

    // Popular

    override fun popularMangaRequest(page: Int): Request = searchMangaRequest(sort = "popular")

    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)

    // Latest

    override fun latestUpdatesRequest(page: Int): Request = searchMangaRequest()

    override fun latestUpdatesParse(response: Response): MangasPage = searchMangaParse(response)

    // Search

    private fun searchMangaRequest(page: Int = 1, query: String? = null, sort: String? = null): Request {
        val url = "$baseUrl/api/obras".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            query?.let { addQueryParameter("q", it) }
            sort?.let { addQueryParameter("sort", it) }
        }.build()
        return GET(url, headers)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = searchMangaRequest(page, query)

    override fun searchMangaParse(response: Response): MangasPage {
        val dto = response.parseAs<Mangas>()

        return MangasPage(dto.series.map { it.toSManga(baseUrl) }, hasNextPage = dto.page < dto.totalPages)
    }

    // Details

    override fun getMangaUrl(manga: SManga) = "$baseUrl/title/${manga.getSlug()}"

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$baseUrl/title/${manga.getSlug()}", headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("meta[property*=title]")!!.text().substringBeforeLast("|")
            thumbnail_url = document.selectFirst("img.object-cover")?.absUrl("src")
            description = document.selectFirst("div p.text-neutral-300.text-sm")?.text()
            genre = document.select("a[href*='?genre=']").joinToString { it.text() }
        }
    }

    // Chapters

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val slug = response.request.url.pathSegments.last()
        return response.extractNextJs<ChapterList>()!!.chapters.map { it.toSChapter() }
    }

    // Pages

    override fun pageListRequest(chapter: SChapter) = GET("$baseUrl/api/viewer/bootstrap?c=${chapter.getId()}", headers)

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> = client.newCall(pageListRequest(chapter)).asObservableSuccess().map { res ->
        pageListParse(res)
    }

    override fun pageListParse(response: Response): List<Page> {
        val pages = response.parseAs<PagesList>()

        return pages.pages.map { page ->
            Page(page.i, imageUrl = "${page.u.trim('/')}")
        }
    }

    override fun imageRequest(page: Page): Request = GET(page.imageUrl!!, imgHeaders)

    override fun imageUrlParse(response: Response): String = ""

    // backward compatibility
    private fun SManga.getSlug(): String = url.substringAfterLast('/')

    private fun SChapter.getId(): String = if (url.contains("ler/") || !url.startsWith('/')) {
        url.trim('/').substringAfterLast('/')
    } else {
        error("Atualizar o mangá")
    }
}
