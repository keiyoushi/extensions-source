package eu.kanade.tachiyomi.extension.pt.remangas

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response

class NoxManga : HttpSource() {

    override val name: String = "NoxManga"

    override val baseUrl: String = "https://noxmanga.co"

    override val lang: String = "pt-BR"

    override val supportsLatest: Boolean = true

    override val id: Long = 7462657023971681136

    private val apiUrl: String = "https://xodneo.site/api/v1/comics"

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .set("Origin", baseUrl)

    // ====================== Popular ====================================

    override fun popularMangaRequest(page: Int): Request {
        val url = apiUrl.toHttpUrl().newBuilder()
            .addQueryParameter("per_page", "20")
            .addQueryParameter("sort", "popular")
            .addQueryParameter("period", "week")
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val dto = response.parseAs<PageableDto<MangaDto>>()
        val mangas = dto.list.map(MangaDto::toSManga)
        return MangasPage(mangas, hasNextPage = dto.hasNextPage())
    }

    // ====================== Latest ====================================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = apiUrl.toHttpUrl().newBuilder()
            .addQueryParameter("per_page", "20")
            .addQueryParameter("sort", "latest")
            .addQueryParameter("period", "week")
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // ====================== Search ====================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // ====================== Details ====================================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst(".detail-title")!!.text()
            thumbnail_url = document.selectFirst(".detail-cover img")?.absUrl("src")
            description = document.selectFirst(".detail-description")?.text()
            genre = document.select(".detail-tags a").joinToString { it.text() }
            genre = document.select(".detail-tags a").joinToString { it.text() }
            document.selectFirst(".status-badge")?.text()?.let {
                status = when (it.lowercase()) {
                    "em andamento" -> SManga.ONGOING
                    "hiato" -> SManga.ONGOING
                    "completo" -> SManga.COMPLETED
                    "cancelado" -> SManga.CANCELLED
                    else -> SManga.UNKNOWN
                }
            }
        }
    }

    // ====================== Chapters ====================================

    override fun chapterListRequest(manga: SManga): Request {
        val slug = manga.url.substringAfterLast("/")
        val url = "$apiUrl/slug/$slug/chapters".toHttpUrl().newBuilder()
            .addQueryParameter("page", "1")
            .addQueryParameter("per_page", "999")
            .addQueryParameter("sort", "newest")
            .build()
        return GET(url, headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val pathSegments = response.request.url.pathSegments
        val dto = response.parseAs<PageableDto<ChapterDto>>()
        return dto.list.map { it.toSChapter(pathSegments[pathSegments.size - 2]) }
    }

    override fun pageListParse(response: Response): List<Page> = response.asJsoup().select("section > img").mapIndexed { index, element ->
        Page(index, imageUrl = element.absUrl("src"))
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()
}
