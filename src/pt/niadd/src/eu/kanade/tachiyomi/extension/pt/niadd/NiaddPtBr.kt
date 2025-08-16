package eu.kanade.tachiyomi.extension.pt.niadd

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import okhttp3.Request

class NiaddPtBr : ParsedHttpSource() {

    override val name = "Niadd Pt-BR"
    override val baseUrl = "https://br.niadd.com"
    override val lang = "pt-BR"
    override val supportsLatest = true

    // ----------------- POPULAR -----------------
    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/", headers)

    override fun popularMangaSelector(): String = ".carousel-item" // Carrossel da home

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()

        // Link do mangá
        manga.setUrlWithoutDomain(
            element.selectFirst(".banner-read-link")?.attr("href") ?: ""
        )

        // Título
        manga.title = element.selectFirst(".banner-manga-title")?.text()?.trim() ?: "Sem título"

        // Imagem
        manga.thumbnail_url = element.selectFirst("img")?.attr("src")?.trim() ?: ""

        // Autor / artista
        val author = element.selectFirst(".banner-manga-author")?.text()?.trim()
        manga.author = author
        manga.artist = author

        // Descrição / intro
        manga.description = element.selectFirst(".product-intro span")?.text()?.trim() ?: ""

        return manga
    }

    override fun popularMangaNextPageSelector(): String? = null // Só home por enquanto

    // ----------------- LATEST -----------------
    override fun latestUpdatesRequest(page: Int): Request = popularMangaRequest(page)
    override fun latestUpdatesSelector(): String = popularMangaSelector()
    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)
    override fun latestUpdatesNextPageSelector(): String? = null

    // ----------------- SEARCH -----------------
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
        GET("$baseUrl/?s=$query", headers)

    override fun searchMangaSelector(): String = ".carousel-item"
    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)
    override fun searchMangaNextPageSelector(): String? = null

    // ----------------- DETAILS -----------------
    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        manga.title = document.selectFirst("h1")?.text()?.trim() ?: "Sem título"
        manga.author = document.selectFirst(".bookside-bookinfo div[itemprop=author] span.bookside-bookinfo-value")?.text()?.trim()
        manga.artist = manga.author
        manga.description = document.selectFirst(".detail-synopsis")?.text()?.trim() ?: ""
        manga.thumbnail_url = document.selectFirst(".detail-cover img")?.attr("src")?.trim() ?: ""
        manga.status = SManga.UNKNOWN
        return manga
    }

    override fun chapterListRequest(manga: SManga): Request =
        GET(baseUrl + manga.url.removeSuffix("/") + "/chapters.html", headers)

    override fun chapterListSelector(): String = "ul.chapter-list a.hover-underline"
    override fun chapterFromElement(element: Element) = eu.kanade.tachiyomi.source.model.SChapter.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        name = element.selectFirst("span.chp-title")?.text()?.trim() ?: element.text().trim()
    }

    override fun pageListParse(document: Document) = listOf<eu.kanade.tachiyomi.source.model.Page>() // ainda vazio
    override fun imageUrlParse(document: Document) = ""
}
