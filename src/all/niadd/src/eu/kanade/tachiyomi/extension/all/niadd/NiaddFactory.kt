package eu.kanade.tachiyomi.extension.en.niadd

import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class NiaddFactory : SourceFactory {
    override fun createSources() = listOf(
        NiaddEn()
    )
}

// Classe base genérica para todos os idiomas Niadd
abstract class NiaddBaseLang(
    override val name: String,
    override val baseUrl: String,
    override val lang: String
) : ParsedHttpSource() {

    override val supportsLatest = true

    // Busca — agora com filtro incluído, mas pode ignorar
    protected abstract fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request

    // O ParsedHttpSource exige esse método que chama o de cima passando filtro vazio
    override fun searchMangaRequest(page: Int, query: String): Request =
        searchMangaRequest(page, query, FilterList())

    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        val link = element.selectFirst("td.manga-part a")!!
        setUrlWithoutDomain(link.attr("href"))
        title = link.text()
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
    }

    override fun searchMangaNextPageSelector() = "a:contains(Next)"

    // Lista de populares
    override fun popularMangaRequest(page: Int) =
        GET("$baseUrl/popular/", headers)

    override fun popularMangaFromElement(element: Element) = searchMangaFromElement(element)

    override fun popularMangaNextPageSelector() = searchMangaNextPageSelector()

    // Últimos lançamentos
    override fun latestUpdatesRequest(page: Int) =
        GET("$baseUrl/latest/", headers)

    override fun latestUpdatesFromElement(element: Element) = searchMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = searchMangaNextPageSelector()

    // Detalhes do mangá
    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.selectFirst("h1")?.text().orEmpty()
        author = document.select("span:contains(Author) + a").joinToString { it.text() }
        artist = author
        description = document.selectFirst("#show-summary")?.text()
        genre = document.select("a[href*=/genre/]").joinToString { it.text() }
        status = when (document.selectFirst("span:contains(Status)")?.ownText()?.trim()) {
            "Ongoing" -> SManga.ONGOING
            "Completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        thumbnail_url = document.selectFirst("div.book-cover img")?.absUrl("src")
    }

    // Lista de capítulos
    override fun chapterListSelector() = "ul#episode-list-1 li a"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        name = element.text()
    }

    // Páginas do capítulo
    override fun pageListParse(document: Document): List<Page> {
        return document.select("div#image-container img").mapIndexed { i, el ->
            Page(i, "", el.absUrl("src"))
        }
    }

    override fun imageUrlParse(document: Document) =
        throw UnsupportedOperationException("Not used")
}

// Implementação só para inglês
class NiaddEn : NiaddBaseLang(
    name = "Niadd (English)",
    baseUrl = "https://www.niadd.com",
    lang = "en"
) {
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search/?keyword=$query&page=$page"
        return GET(url, headers)
    }
}
