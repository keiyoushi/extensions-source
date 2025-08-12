package eu.kanade.tachiyomi.extension.all.niadd

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.*

open class Niadd(
    override val name: String,
    override val baseUrl: String,
    private val langCode: String,
) : ParsedHttpSource() {

    override val lang: String = langCode
    override val supportsLatest: Boolean = true

    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/category/?page=$page", headers)

    override fun popularMangaSelector(): String = "div.manga-item"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        val link = element.selectFirst("a")!!
        manga.setUrlWithoutDomain(link.attr("href"))
        manga.title = element.selectFirst("h3")?.text() ?: ""
        manga.thumbnail_url = element.selectFirst("img")?.absUrl("src")
        return manga
    }

    override fun popularMangaNextPageSelector(): String? = "a.next"

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/category/last_update/?page=$page", headers)

    override fun latestUpdatesSelector(): String = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga =
        popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector(): String? = popularMangaNextPageSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
        GET("$baseUrl/search/?name=$query&page=$page", headers)

    override fun searchMangaSelector(): String = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga =
        popularMangaFromElement(element)

    override fun searchMangaNextPageSelector(): String? = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()

        // Título
        manga.title = document.selectFirst("h1")?.text() ?: ""

        // Descrição
        manga.description = document.selectFirst("div.detail-desc")?.text()

        // Capa
        manga.thumbnail_url = document.selectFirst("div.detail-cover img")?.absUrl("src")

        // Gêneros
        val genres = document.select("div.detail-info span.genres a").joinToString(", ") { it.text() }
        if (genres.isNotEmpty()) {
            manga.genre = genres
        }

        // Autor e artista - baseado no padrão que aparece na página de detalhes
        val author = document.selectFirst("div.detail-info span.author a")?.text()
        if (!author.isNullOrBlank()) {
            manga.author = author
            manga.artist = author
        }

        return manga
    }

    override fun chapterListRequest(manga: SManga): Request {
        // Garante que vai na página /chapters.html para pegar lista de capítulos
        val chaptersUrl = if (manga.url.endsWith("/chapters.html")) {
            manga.url
        } else {
            manga.url.removeSuffix("/") + "/chapters.html"
        }
        return GET(baseUrl + chaptersUrl, headers)
    }

    // Selector para pegar cada <a> dentro do <ul class="chapter-list">
    override fun chapterListSelector(): String = "ul.chapter-list a.hover-underline"

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()

        // Link do capítulo, relativo ao domínio
        chapter.setUrlWithoutDomain(element.attr("href"))

        // Nome do capítulo (título)
        chapter.name = element.selectFirst("span.chp-title")?.text() ?: element.text()

        // Data do capítulo
        val dateText = element.selectFirst("span.chp-time")?.text()
        chapter.date_upload = parseDate(dateText)

        return chapter
    }

    private fun parseDate(dateString: String?): Long {
        if (dateString.isNullOrBlank()) return 0L
        return try {
            val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.ENGLISH)
            sdf.parse(dateString)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    override fun pageListParse(document: Document): List<Page> =
        document.select("div.reader-page img").mapIndexed { i, img ->
            Page(i, "", img.absUrl("src"))
        }

    override fun imageUrlParse(document: Document): String {
        throw UnsupportedOperationException("Not used")
    }
}
