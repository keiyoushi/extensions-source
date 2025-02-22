package eu.kanade.tachiyomi.extension.es.dragontranslationnet

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element

open class DragonTranslationNet : HttpSource() {

    override val name = "DragonTranslation.net"

    override val baseUrl = "https://dragontranslation.net"

    override val lang = "es"

    override val supportsLatest = true

    // Popular

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/mangas?page=$page", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val entries = document.select("article.card").map {
            SManga.create().apply {
                setUrlWithoutDomain(it.selectFirst("a.lanzador")!!.attr("href"))
                thumbnail_url = it.selectFirst("img")?.attr("abs:src")
                title = it.selectFirst("h2")!!.text()
            }
        }
        val hasNextPage = document.selectFirst("li.page-item a[rel=next]") != null
        return MangasPage(entries, hasNextPage)
    }

    // Latest

    override fun latestUpdatesRequest(page: Int): Request {
        return GET(baseUrl, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangaList = document.select("div#pills-home:lt(1) article").map {
            SManga.create().apply {
                setUrlWithoutDomain(it.selectFirst("a[rel=bookmark]")!!.attr("href"))
                title = it.selectFirst("h2")!!.text()
                thumbnail_url = it.selectFirst("img")?.attr("abs:src")
            }
        }
        return MangasPage(mangaList, false)
    }

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().addPathSegment("mangas")
            .addQueryParameter("buscar", query).addQueryParameter("page", page.toString())
        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // Chapters

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("ul.list-group a").map {
            SChapter.create().apply {
                name = it.selectFirst("li")!!.text()
                setUrlWithoutDomain(it.attr("abs:href"))
            }
        }
    }

    // Details

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val infoRow = document.selectFirst("div.section-main > div.row")
        return SManga.create().apply {
            description = infoRow?.selectFirst("> :eq(1)")?.ownText()
            status = infoRow?.selectFirst("p:contains(Status) > a").parseStatus()
            genre = infoRow?.select("p:contains(Tag(s)) a")?.joinToString { it.text() }
        }
    }

    // Pages

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("div#chapter_imgs img").mapIndexed { index, element ->
            Page(
                index,
                document.location(),
                element.attr("abs:src"),
            )
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // Helpers

    private fun Element?.parseStatus(): Int = when (this?.text()?.lowercase()) {
        "publishing", "ongoing" -> SManga.ONGOING
        "ended" -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }
}
