package eu.kanade.tachiyomi.extension.pt.plumacomics

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response

class PlumaComics : HttpSource() {

    override val name: String = "Pluma Comics"

    override val lang: String = "pt-BR"

    override val baseUrl: String = "https://plumacomics.cloud"

    override val supportsLatest: Boolean = true

    override val client = super.client.newBuilder()
        .rateLimit(3, 1)
        .build()

    override val versionId = 5

    // Popular
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/series?sort=popular", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("a.group[href*=series]").map { element ->
            SManga.create().apply {
                title = element.selectFirst("h3")!!.text()
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
                setUrlWithoutDomain(element.absUrl("href"))
            }
        }
        return MangasPage(mangas, hasNextPage = document.selectFirst("a.btn-primary[href*=page]") != null)
    }

    // Latest

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/series", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/api/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val dto = response.parseAs<SearchDto>()
        val mangas = dto.results.map {
            SManga.create().apply {
                title = it.title
                thumbnail_url = "$baseUrl/api/cover/${it.coverPath}"
                url = "/series/${it.slug}"
            }
        }

        return MangasPage(mangas, hasNextPage = false)
    }

    // Details

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("meta[property*=title]")!!.text().substringBeforeLast("|")
            thumbnail_url = document.selectFirst("img.cover-img")?.absUrl("src")
            description = document.selectFirst("div.card > p.text-sm")?.text()
            genre = document.select(".flex.flex-wrap > span").joinToString { it.text() }
            document.selectFirst(".flex.items-center span.text-xs.font-bold.uppercase:last-child")?.text()?.let {
                status = when (it.lowercase()) {
                    "em andamento" -> SManga.ONGOING
                    else -> SManga.UNKNOWN
                }
            }
            setUrlWithoutDomain(document.location())
        }
    }

    // Chapters

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select(".card a[href*=ler]").mapIndexed { index, element ->
            SChapter.create().apply {
                name = element.selectFirst("span:first-child")!!.text()
                setUrlWithoutDomain(element.absUrl("href"))
            }
        }
    }

    // Pages

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("#chapter-pages img").mapIndexed { index, element ->
            Page(index, imageUrl = element.absUrl("src"))
        }
    }

    override fun imageUrlParse(response: Response): String = ""

    // Utils

    @Serializable
    private class SearchDto(
        val results: List<MangaDto>,
    )

    @Serializable
    private class MangaDto(
        val title: String,
        val slug: String,
        val coverPath: String,
    )
}
