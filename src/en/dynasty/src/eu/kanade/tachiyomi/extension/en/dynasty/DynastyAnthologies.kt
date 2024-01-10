package eu.kanade.tachiyomi.extension.en.dynasty

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document

class DynastyAnthologies : DynastyScans() {

    override val name = "Dynasty-Anthologies"

    override val searchPrefix = "anthologies"

    override fun popularMangaInitialUrl() = ""

    private fun popularMangaInitialUrl(page: Int) = "$baseUrl/search?q=&classes%5B%5D=Anthology&page=$page=$&sort="

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/search?q=$query&classes%5B%5D=Anthology&sort=&page=$page", headers)
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = baseUrl + document.select("div.span2 > img").attr("src")
        parseHeader(document, manga)
        parseGenres(document, manga)
        parseDescription(document, manga)
        return manga
    }

    override fun popularMangaRequest(page: Int): Request {
        return GET(popularMangaInitialUrl(page), headers)
    }

    override fun popularMangaNextPageSelector() = searchMangaNextPageSelector()

    override fun popularMangaSelector() = searchMangaSelector()

    override fun popularMangaParse(response: Response) = searchMangaParse(response)
}
