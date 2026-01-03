package eu.kanade.tachiyomi.extensions.pt.thehentai

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.util.Locale

class TheHentai : ParsedHttpSource() {

    override val name = "The Hentai"
    override val baseUrl = "https://thehentai.net"
    override val lang = "pt"
    override val supportsLatest = true

    // Headers para evitar blocks
    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")

    // Popular Mangas (ajuste o URL e selector conforme inspeção)
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/hentai-populares/page/$page", headers)
    
    override fun popularMangaSelector(): String = "div.manga-item, .comic-item" // Inspecione a homepage para o selector certo, ex: div.grid-item

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.select("a.title, h3").text().trim()
        thumbnail_url = element.select("img").attr("abs:src")
        url = element.select("a").attr("abs:href")
    }

    override fun popularMangaNextPageSelector(): String = "a.next-page, .pagination a[rel=next]" // Selector para próxima página

    // Latest (atualizações recentes)
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/page/$page", headers) // Ou /ultimos-hentai/

    override fun latestUpdatesSelector() = popularMangaSelector()
    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)
    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/busca/".toHttpUrl().newBuilder().addQueryParameter("q", query).addQueryParameter("page", page.toString()).build()
        return GET(url, headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()
    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // Detalhes do Manga
    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.select("h1").text().trim()
        author = document.select("span/artist-link").text().replace("@", "").trim() // Ajuste selector
        artist = author
        genre = document.select("div.tags a").joinToString { it.text().replace("#", "") }
        description = document.select("div.description").text().trim()
        thumbnail_url = document.select("img.cover, .thumbnail img").attr("abs:src") // Ajuste
        status = SManga.COMPLETED // Hentais geralmente são completos
    }

    // Chapters (para hentai, geralmente único)
    override fun chapterListSelector() = "" // Não usa, pois single chapter

    override fun chapterListParse(response: Response): List<SChapter> {
        return listOf(
            SChapter.create().apply {
                name = "Capítulo Único"
                chapter_number = 1f
                url = response.request.url.toString() // Usa a URL do manga como chapter
            }
        )
    }

    // Pages
    override fun pageListParse(document: Document): List<Page> {
        // Extrai o número de páginas, ex: "Conteúdo 22 páginas" -> 22
        val pageCountText = document.select("span:contains(páginas)").text().trim()
        val pageCount = pageCountText.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 1 // Default 1 se falhar

        val slug = document.location().substringAfterLast("/") // Ou parse do url
        val pages = mutableListOf<Page>()
        for (i in 1..pageCount) {
            val pageNum = String.format(Locale.US, "%02d", i)
            val pageUrl = "$baseUrl/$slug/$slug-hentai-pt-br-$pageNum/"
            pages.add(Page(i - 1, pageUrl))
        }
        return pages
    }

    // Extrai a URL da imagem de cada página
    override fun imageUrlParse(document: Document): String {
        return document.select("#img_gallery_big").attr("abs:src") // Ou "source[type=image/webp]").attr("srcset") para webp
    }

    // Se precisar de filters (tags, etc.), adicione override fun getFilterList()
}
