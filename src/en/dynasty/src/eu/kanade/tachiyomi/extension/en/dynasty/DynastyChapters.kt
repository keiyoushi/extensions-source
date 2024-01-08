package eu.kanade.tachiyomi.extension.en.dynasty

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class DynastyChapters : DynastyScans() {
    override val name = "Dynasty-Chapters"
    override val searchPrefix = "chapters"
    override fun popularMangaInitialUrl() = ""

    private fun popularMangaInitialUrl(page: Int) = "$baseUrl/search?q=&classes%5B%5D=Chapter&page=$page=$&sort="
    private fun latestUpdatesInitialUrl(page: Int) = "$baseUrl/search?q=&classes%5B%5D=Chapter&page=$page=$&sort=created_at"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/search?q=$query&classes%5B%5D=Chapter&sort=&page=$page", headers)
    }

    override val supportsLatest = true

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()

        manga.thumbnail_url = document.select("img").last()!!.absUrl("src")
        manga.title = document.select("h3 b").text()
        manga.status = SManga.COMPLETED
        val artistAuthorElements = document.select("a[href*=author]")
        if (!artistAuthorElements.isEmpty()) {
            if (artistAuthorElements.size == 1) {
                manga.author = artistAuthorElements[0].text()
            } else {
                manga.artist = artistAuthorElements[0].text()
                manga.author = artistAuthorElements[1].text()
            }
        }

        val genreElements = document.select(".tags a")
        val doujinElements = document.select("a[href*=doujins]")
        genreElements.addAll(doujinElements)
        parseGenres(genreElements, manga)

        return manga
    }

    override fun searchMangaSelector() = "dd"

    override fun searchMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        val titleSelect = element.select("a.name")
        manga.title = titleSelect.text()
        manga.setUrlWithoutDomain(titleSelect.attr("href"))
        return manga
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        return document.select(chapterListSelector()).map {
            chapterFromElement(it)
        }
    }

    override fun chapterListSelector() = ".chapters.show#main"

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(element.baseUri())
        chapter.name = element.select("h3").text()
        chapter.date_upload = element.select("span.released").firstOrNull()?.text().toDate("MMM dd, yyyy")
        return chapter
    }

    override fun popularMangaRequest(page: Int): Request {
        return GET(popularMangaInitialUrl(page), headers)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET(latestUpdatesInitialUrl(page), headers)
    }

    override fun popularMangaNextPageSelector() = searchMangaNextPageSelector()
    override fun latestUpdatesNextPageSelector() = searchMangaNextPageSelector()

    override fun popularMangaSelector() = searchMangaSelector()
    override fun latestUpdatesSelector() = searchMangaSelector()

    override fun popularMangaFromElement(element: Element) = searchMangaFromElement(element)
    override fun latestUpdatesFromElement(element: Element) = searchMangaFromElement(element)

    override fun popularMangaParse(response: Response) = searchMangaParse(response)
    override fun latestUpdatesParse(response: Response) = searchMangaParse(response)
}
