package eu.kanade.tachiyomi.extension.es.tojimangas

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class Tojimangas : ParsedHttpSource() {

    override val lang = "es"
    override val baseUrl = "https://tojimangas.com"
    override val name = "Tojimangas"
    override val supportsLatest = true

    override fun searchMangaSelector(): String = ".animposx a"
    override fun popularMangaSelector(): String = searchMangaSelector()
    override fun latestUpdatesSelector(): String = searchMangaSelector()

    override fun searchMangaNextPageSelector(): String = ".arrow_pag"
    override fun popularMangaNextPageSelector(): String = searchMangaNextPageSelector()
    override fun latestUpdatesNextPageSelector(): String = searchMangaNextPageSelector()

    override fun chapterListSelector(): String = "li .epsleft"

    private fun makeMangaRequest(
        page: Int,
        addToBuilder: (HttpUrl.Builder) -> HttpUrl.Builder,
    ): Request = GET(
        addToBuilder(
            baseUrl.toHttpUrl().newBuilder().apply {
                addPathSegment("biblioteca")
                addPathSegment("page")
                addPathSegment(page.toString())
            },
        ).build(),
        headers,
    )

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
        makeMangaRequest(page) {
            it.addQueryParameter("title", query)
        }

    override fun popularMangaRequest(page: Int): Request = makeMangaRequest(page) {
        it.addQueryParameter("order", "popular")
    }

    override fun latestUpdatesRequest(page: Int): Request = makeMangaRequest(page) {
        it.addQueryParameter("order", "update")
    }

    override fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.absUrl("href"))
        title = element.attr("title")
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
    }

    override fun popularMangaFromElement(element: Element): SManga = searchMangaFromElement(element)

    override fun latestUpdatesFromElement(element: Element): SManga =
        searchMangaFromElement(element)

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        thumbnail_url = document.selectFirst("thumb img")?.absUrl("src")

        description =
            document.select(".desc > div > :not(h2, [style='display: none;'], .manga-update-info)")
                .filter { p -> p.text() != "" }
                .joinToString { p -> p.text() }

        genre = document.select(".genre-info a").joinToString { a -> a.text() }

        val infoGrid = document.selectFirst(".infox .spe")

        author = infoGrid?.selectFirst("span:contains(Autor)")?.text()?.substringAfter("Autor")

        status =
            when (
                infoGrid?.selectFirst("span:contains(Estado)")?.text()?.substringAfter("Estado")
                    ?.trim()?.lowercase()
            ) {
                "activo" -> SManga.ONGOING
                "finalizado" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
    }

    private val dateFormat = SimpleDateFormat("dd MMMM, yyyy", Locale("es"))

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val a = element.selectFirst("a")
        a?.absUrl("href")?.let { setUrlWithoutDomain(it) }
        name = a?.text() ?: ""
        date_upload = element.selectFirst(".date")?.text()?.let { dateFormat.parse(it)?.time } ?: 0
    }

    override fun pageListParse(document: Document): List<Page> =
        document.select(".reader-area img").mapIndexed { idx, img ->
            Page(idx, imageUrl = img.absUrl("src"))
        }

    override fun imageUrlParse(document: Document): String = ""
}
