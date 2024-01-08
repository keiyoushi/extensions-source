package eu.kanade.tachiyomi.extension.en.mangapill

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.Locale

class MangaPill : ParsedHttpSource() {

    override val name = "MangaPill"
    override val baseUrl = "https://mangapill.com"
    override val lang = "en"
    override val supportsLatest = true
    override val client: OkHttpClient = network.cloudflareClient
    override fun headersBuilder() = super.headersBuilder().add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request = latestUpdatesRequest(page)

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/chapters", headers)
    }

    override fun popularMangaSelector() = latestUpdatesSelector()
    override fun latestUpdatesSelector() = ".grid > div:not([class])"
    override fun searchMangaSelector() = latestUpdatesSelector()

    override fun popularMangaFromElement(element: Element): SManga = latestUpdatesFromElement(element)

    override fun latestUpdatesFromElement(element: Element): SManga = SManga.create().apply {
        thumbnail_url = element.selectFirst("img")!!.attr("data-src")
        setUrlWithoutDomain(element.selectFirst("a[href^='/manga/']")!!.attr("href"))
        title = element.selectFirst("a:not(:first-child) > div")?.text() ?: ""
    }

    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        thumbnail_url = element.selectFirst("img")!!.attr("data-src")
        setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
        title = element.selectFirst("div[class] > a")?.text() ?: ""
    }

    override fun popularMangaNextPageSelector() = null
    override fun latestUpdatesNextPageSelector() = null
    override fun searchMangaNextPageSelector() = "a.btn.btn-sm"

    override fun mangaDetailsParse(document: Document): SManga {
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

    override fun chapterListSelector() = "#chapters > div > a"

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.attr("href")
        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(urlElement)
        chapter.name = element.text()
        chapter.date_upload = 0
        return chapter
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        document.select("picture img").forEachIndexed { i, it ->
            pages.add(Page(i, "", it.attr("data-src")))
        }
        return pages
    }

    override fun imageUrlParse(document: Document) = ""
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search".toHttpUrlOrNull()!!.newBuilder()
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
        return GET(url.toString(), headers)
    }

    private class Type : UriPartFilter(
        "Type",
        arrayOf(
            Pair("All", ""),
            Pair("Manga", "manga"),
            Pair("Novel", "novel"),
            Pair("One-Shot", "one-shot"),
            Pair("Doujinshi", "doujinshi"),
            Pair("Manhwa", "manhwa"),
            Pair("Manhua", "manhua"),
            Pair("Oel", "oel"),
        ),
    )

    private class Genre(name: String, val id: String = name) : Filter.TriState(name)
    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Genres", genres)
    private class Status : UriPartFilter(
        "Status",
        arrayOf(
            Pair("All", ""),
            Pair("Publishing", "publishing"),
            Pair("Finished", "finished"),
            Pair("On Hiatus", "on hiatus"),
            Pair("Discontinued", "discontinued"),
            Pair("Not yet Published", "not yet published"),
        ),
    )

    override fun getFilterList() = FilterList(
        Filter.Header("NOTE: Ignored if using text search!"),
        Filter.Separator(),
        Status(),
        Type(),
        GenreList(getGenreList()),
    )

    private fun getGenreList() = listOf(
        Genre("Action"),
        Genre("Adventure"),
        Genre("Cars"),
        Genre("Comedy"),
        Genre("Dementia"),
        Genre("Demons"),
        Genre("Drama"),
        Genre("Ecchi"),
        Genre("Fantasy"),
        Genre("Game"),
        Genre("Harem"),
        Genre("Hentai"),
        Genre("Historical"),
        Genre("Horror"),
        Genre("Josei"),
        Genre("Kids"),
        Genre("Magic"),
        Genre("Martial Arts"),
        Genre("Mecha"),
        Genre("Military"),
        Genre("Music"),
        Genre("Mystery"),
        Genre("Parody"),
        Genre("Police"),
        Genre("Psychological"),
        Genre("Romance"),
        Genre("Samurai"),
        Genre("School"),
        Genre("Sci-Fi"),
        Genre("Seinen"),
        Genre("Shoujo"),
        Genre("Shoujo Ai"),
        Genre("Shounen"),
        Genre("Shounen Ai"),
        Genre("Slice of Life"),
        Genre("Space"),
        Genre("Sports"),
        Genre("Super Power"),
        Genre("Supernatural"),
        Genre("Thriller"),
        Genre("Vampire"),
        Genre("Yaoi"),
        Genre("Yuri"),
    )

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }
}
