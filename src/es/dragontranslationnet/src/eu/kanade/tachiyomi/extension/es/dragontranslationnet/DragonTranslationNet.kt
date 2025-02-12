package eu.kanade.tachiyomi.extension.es.dragontranslationnet

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class DragonTranslationNet :
    Madara("DragonTranslation.net", "https://dragontranslation.net", "es") {

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/mangas?page=$page", headers)
    }

    override fun popularMangaSelector() = "article.card"

    override fun popularMangaNextPageSelector() = "li.page-item a[rel=next]"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.selectFirst("a.lanzador")!!.attr("href"))
        thumbnail_url = element.selectFirst("img")?.attr("abs:src")
        title = element.selectFirst("h2")!!.text()
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET(baseUrl, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val latestMangaContainer = document.selectFirst("div#pills-home")
        val mangaList = latestMangaContainer!!.select("div > div").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.selectFirst("a[rel=bookmark]")!!.attr("href"))
                title = element.selectFirst("h2")!!.text()
                thumbnail_url = element.selectFirst("img")?.attr("abs:src")
            }
        }
        return MangasPage(mangaList, false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("mangas")
            .addQueryParameter("buscar", query)
            .addQueryParameter("page", page.toString())

        return GET(url.build(), headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun getFilterList() = FilterList(emptyList())

    override fun mangaDetailsParse(document: Document): SManga {
        val infoRow = document.selectFirst("div.section-main > div.row")

        return SManga.create().apply {
            description = infoRow?.selectFirst("> :eq(1)")?.ownText()
            status = infoRow?.selectFirst("p:contains(Status) > a").parseStatus()
            genre = infoRow?.select("p:contains(Tag(s)) a")?.joinToString { it.text() }
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        return document.select("ul.list-group a").map {
            SChapter.create().apply {
                name = it.selectFirst("li")!!.text()
                url = it.attr("abs:href")
            }
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("div#chapter_imgs img").mapIndexed { index, element ->
            Page(
                index,
                document.location(),
                element.attr("abs:src"),
            )
        }
    }

    protected fun Element?.parseStatus(): Int = when (this?.text()?.lowercase()) {
        "publishing", "ongoing" -> SManga.ONGOING
        "ended" -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }
}
