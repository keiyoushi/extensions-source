package eu.kanade.tachiyomi.extension.en.webcomics

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class Webcomics : ParsedHttpSource() {

    override val name = "Webcomics"

    override val baseUrl = "https://www.webcomicsapp.com"

    override val lang = "en"

    override val supportsLatest = true

    override fun popularMangaSelector() = "section.mangas div div.col-md-3"

    override fun latestUpdatesSelector() = "section.mangas div div.col-md-3"

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "https://www.webcomicsapp.com")

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/popular.html", headers)

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/latest.html", headers)

    private fun mangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("a").first()!!.let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.select("h5").text()
        }
        return manga
    }

    override fun popularMangaFromElement(element: Element) = mangaFromElement(element)

    override fun latestUpdatesFromElement(element: Element) = mangaFromElement(element)

    override fun popularMangaNextPageSelector() = null

    override fun latestUpdatesNextPageSelector() = null

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("section.book-info-left > .wrap")

        val manga = SManga.create()
        manga.genre = infoElement.select(".labels > label").joinToString(", ") { it.text() }
        manga.description = infoElement.select("p.p-description").text()
        manga.thumbnail_url = infoElement.select("img").first()?.attr("src")
        infoElement.select("p.p-schedule:first-of-type").text().let {
            if (it.contains("IDK")) manga.status = SManga.COMPLETED else manga.status = SManga.ONGOING
        }
        return manga
    }

    override fun searchMangaNextPageSelector() = throw UnsupportedOperationException("Not used")

    override fun searchMangaFromElement(element: Element): SManga {
        val infoElement = element.select(".col-md-5")
        val manga = SManga.create()
        infoElement.let {
            manga.title = it.select(".wiki-book-title").text().trim()
            manga.setUrlWithoutDomain(it.select("a").first()!!.attr("href"))
        }
        return manga
    }

    override fun searchMangaSelector() = ".wiki-book-list > .row"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/wiki.html?search=$query&page=$page".toHttpUrlOrNull()?.newBuilder()
        (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
            when (filter) {
                is GenreFilter -> {
                    val genre = getGenreList()[filter.state]
                    url?.addQueryParameter("category", genre)
                }
                else -> {}
            }
        }
        return GET(url.toString(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        var nextPage = true
        val mangas = document.select(searchMangaSelector()).toList().filter {
            val shouldFilter = it.select(".col-md-2 > a").first()!!.text() == "READ"
            if (nextPage) {
                nextPage = shouldFilter
            }
            shouldFilter
        }.map { element ->
            searchMangaFromElement(element)
        }

        return MangasPage(mangas, if (nextPage) hasNextPage(document) else false)
    }

    private fun hasNextPage(document: Document): Boolean {
        return !document.select(".pagination .page-item.active + .page-item").isEmpty()
    }

    override fun chapterListSelector() = "section.book-info-left > .wrap > ul > li"

    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        /* Source only allows 20 chapters to be readable on their website, trying to read past
           that results in a page list empty error; so might as well not grab them. */
        return if (document.select("${chapterListSelector()}:nth-child(21)").isEmpty()) {
            document.select(chapterListSelector()).asReversed().map { chapterFromElement(it) }
        } else {
            val chapters = mutableListOf<SChapter>()
            for (i in 1..20)
                document.select("${chapterListSelector()}:nth-child($i)").map { chapters.add(chapterFromElement(it)) }
            // Add a chapter notifying the user of the situation
            val lockedNotification = SChapter.create()
            lockedNotification.name = "[Attention] Additional chapters are restricted by the source to their own app"
            lockedNotification.url = "wiki.html"
            chapters.add(lockedNotification)
            chapters.reversed()
        }
    }

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select("a")

        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(urlElement.attr("href"))
        chapter.name = urlElement.text().trim()
        return chapter
    }

    override fun mangaDetailsRequest(manga: SManga) = GET(baseUrl + "/" + manga.url, headers)

    override fun pageListRequest(chapter: SChapter) = GET(baseUrl + "/" + chapter.url, headers)

    override fun pageListParse(document: Document) = document
        .select("section.book-reader .img-list > li > img")
        .mapIndexed {
                i, element ->
            Page(i, "", element.attr("data-original"))
        }

    override fun imageUrlParse(document: Document) = ""

    private class GenreFilter(genres: Array<String>) : Filter.Select<String>("Genre", genres)

    override fun getFilterList() = FilterList(
        GenreFilter(getGenreList()),
    )

    // [...$('.row.wiki-book-nav .col-md-8 ul a')].map(el => `"${el.textContent.trim()}"`).join(',\n')
    // https://www.webcomicsapp.com/wiki.html
    private fun getGenreList() = arrayOf(
        "All",
        "Fantasy",
        "Comedy",
        "Drama",
        "Modern",
        "Action",
        "Monster",
        "Romance",
        "Boys'Love",
        "Harem",
        "Thriller",
        "Historical",
        "Sci-fi",
        "Slice of Life",
    )
}
