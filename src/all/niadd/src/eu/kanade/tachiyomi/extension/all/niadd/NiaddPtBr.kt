package eu.kanade.tachiyomi.extension.all.niadd

import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.FilterList

class NiaddPtBr : ParsedHttpSource() {

    override val name = "Niadd Pt-BR"
    override val baseUrl = "https://br.niadd.com"
    override val lang = "pt-BR"
    override val supportsLatest = true

    // Popular (exemplo mínimo)
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/category/?page=$page", headers)
    }

    override fun popularMangaSelector(): String = "div.manga-item:has(a[href*='/manga/'])"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        val link = element.selectFirst("a[href*='/manga/']") ?: return manga
        manga.setUrlWithoutDomain(link.attr("href"))
        manga.title = element.selectFirst("div.manga-name")?.text()?.trim() ?: link.text().trim()
        return manga
    }

    override fun popularMangaNextPageSelector(): String? = "a.next"

    // Latest (apenas pra compilar)
    override fun latestUpdatesRequest(page: Int) = popularMangaRequest(page)
    override fun latestUpdatesSelector() = popularMangaSelector()
    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)
    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    // Search (apenas pra compilar)
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = popularMangaRequest(page)
    override fun searchMangaSelector() = popularMangaSelector()
    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // Details mínimo
    override fun mangaDetailsParse(document: Document) = SManga.create()

    // Chapters mínimo
    override fun chapterListRequest(manga: SManga) = GET(manga.url, headers)
    override fun chapterListSelector() = ""
    override fun chapterFromElement(element: Element) = SChapter.create()

    // Pages mínimo
    override fun pageListParse(document: Document) = emptyList<Page>()
    override fun imageUrlParse(document: Document) = ""
}
