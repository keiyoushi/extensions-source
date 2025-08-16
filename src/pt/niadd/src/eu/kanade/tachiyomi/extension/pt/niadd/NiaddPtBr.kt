package eu.kanade.tachiyomi.extension.pt.niadd

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class NiaddPtBr : ParsedHttpSource() {

    override val name = "Niadd Pt-BR"
    override val baseUrl = "https://br.niadd.com"
    override val lang = "pt-BR"
    override val supportsLatest = true

    // Popular
    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/category/?page=$page", headers)
    override fun popularMangaSelector(): String = "div.manga-item"
    override fun popularMangaFromElement(element: Element): SManga = SManga.create()
    override fun popularMangaNextPageSelector(): String? = null

    // Latest
    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/list/New-Update/?page=$page", headers)
    override fun latestUpdatesSelector(): String = "div.manga-item"
    override fun latestUpdatesFromElement(element: Element): SManga = SManga.create()
    override fun latestUpdatesNextPageSelector(): String? = null

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
        GET("$baseUrl/search/?name=$query&page=$page", headers)
    override fun searchMangaSelector(): String = "div.manga-item"
    override fun searchMangaFromElement(element: Element): SManga = SManga.create()
    override fun searchMangaNextPageSelector(): String? = null

    // Details
    override fun mangaDetailsParse(document: Document): SManga = SManga.create()

    // Chapters
    override fun chapterListRequest(manga: SManga): Request = GET(baseUrl, headers)
    override fun chapterListSelector(): String = ""
    override fun chapterFromElement(element: Element): SChapter = SChapter.create()

    // Pages
    override fun pageListParse(document: Document): List<Page> = emptyList()
    override fun imageUrlParse(document: Document): String = ""
}
