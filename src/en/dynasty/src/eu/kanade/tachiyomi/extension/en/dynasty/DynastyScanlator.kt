package eu.kanade.tachiyomi.extension.en.dynasty

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class DynastyScanlator : DynastyScans() {
    override val name = "Dynasty-Scanlator"
    override val searchPrefix = "scanlators"
    override val categoryPrefix = "Scanlator"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET(
            "$baseUrl/search?q=$query&classes%5B%5D=$categoryPrefix&page=$page&sort=",
            headers,
        )
    }

    override fun popularMangaInitialUrl() = ""

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.setUrlWithoutDomain(element.select("a").attr("href"))
        manga.title = element.select("div.caption").text()
        return manga
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        parseHeader(document, manga)
        return manga
    }

    override fun chapterListSelector() = "dl.chapter-list > dd"

    override fun chapterListParse(response: Response): List<SChapter> {
        return super.chapterListParse(response).reversed()
    }
}
