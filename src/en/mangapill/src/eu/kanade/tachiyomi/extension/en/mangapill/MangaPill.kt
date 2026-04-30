package eu.kanade.tachiyomi.extension.en.mangapill

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import java.util.Locale

class MangaPill : HttpSource() {

    override val name = "MangaPill"
    override val baseUrl = "https://mangapill.com"
    override val lang = "en"
    override val supportsLatest = true
    override val client: OkHttpClient = network.cloudflareClient
    override fun headersBuilder() = super.headersBuilder().add("Referer", "$baseUrl/")

    // Popular fetches the homepage where the "Trending Mangas" section is
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/", headers)

    // Latest fetches the /chapters url
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/chapters", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div:has(h4:contains(Trending)) > .grid > div:not([class])").map { element ->
            latestUpdatesFromElement(element)
        }
        return MangasPage(mangas, false)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".grid > div:not([class])").map { element ->
            latestUpdatesFromElement(element)
        }
        return MangasPage(mangas, false)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".grid > div:not([class])").map { element ->
            latestUpdatesFromElement(element)
        }
        val hasNextPage = document.selectFirst("a.btn.btn-sm") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun latestUpdatesFromElement(element: Element): SManga = SManga.create().apply {
        thumbnail_url = element.selectFirst("img")!!.attr("data-src")
        setUrlWithoutDomain(element.selectFirst("a[href^='/manga/']")!!.absUrl("href"))
        title = element.selectFirst("div.line-clamp-2")!!.text()
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val manga = SManga.create()
        manga.author = ""
        manga.artist = ""
        val genres = mutableListOf<String>()
        document.select("a[href*=genre]").forEach { element ->
            val genre = element.text()
            genres.add(genre)
        }
        manga.genre = genres.joinToString(", ")
        manga.status = parseStatus(document.select("div.container > div:first-child > div:last-child > div:nth-child(3) > div:nth-child(2) > div").text())
        manga.description = document.select("div.container > div:first-child > div:last-child > div:nth-child(2) > p").text()
        manga.thumbnail_url = document.select("div.container > div:first-child > div:first-child > img").first()!!.attr("data-src")

        return manga
    }

    private fun parseStatus(element: String): Int = when {
        element.lowercase(Locale.ENGLISH).contains("publishing") -> SManga.ONGOING
        element.lowercase(Locale.ENGLISH).contains("finished") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("#chapters > div > a").map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                name = element.text()
                date_upload = 0
            }
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("picture img").mapIndexed { i, it ->
            Page(i, imageUrl = it.attr("data-src"))
        }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("q", query)

        filters.forEach { filter ->
            when (filter) {
                is GenreList -> {
                    val genreInclude = mutableListOf<String>()
                    filter.state.forEach {
                        if (it.state == 1) {
                            genreInclude.add(it.id)
                        }
                    }
                    if (genreInclude.isNotEmpty()) {
                        genreInclude.forEach { genre ->
                            url.addQueryParameter("genre", genre)
                        }
                    }
                }

                is Status -> url.addQueryParameter("status", filter.toUriPart())

                is Type -> url.addQueryParameter("type", filter.toUriPart())

                else -> {}
            }
        }
        return GET(url.build(), headers)
    }

    override fun getFilterList() = FilterList(
        Filter.Header("NOTE: Ignored if using text search!"),
        Filter.Separator(),
        Status(),
        Type(),
        GenreList(getGenreList()),
    )
}
