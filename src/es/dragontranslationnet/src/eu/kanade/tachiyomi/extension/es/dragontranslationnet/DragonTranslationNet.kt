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

class DragonTranslationNet : Madara("DragonTranslation.net", "https://dragontranslation.net", "es") {

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/mangas?page=$page", headers)
    }

    override fun popularMangaSelector() = "div:has(> div.series-card)"

    override fun popularMangaNextPageSelector() = "li.page-item a[rel=next]"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.select("div.series-box a").attr("href"))
        thumbnail_url = element.select("img.thumb-img").attr("abs:src")
        title = element.select(".series-title").text()
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET(baseUrl, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val latestMangaContainer = document.selectFirst("div.d-flex:has(div.series-card)")
        val mangaList = latestMangaContainer!!.select("> div").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.select("div.series-box a").attr("href"))
                title = element.select(".series-title").text()
                thumbnail_url = element.select("img.thumb-img").attr("abs:src")
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

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()

        with(element) {
            select(chapterUrlSelector).first()?.let { urlElement ->
                chapter.url = urlElement.attr("abs:href")
                chapter.name = urlElement.select("p.chapter-manhwa-title").text()
                chapter.date_upload = parseChapterDate(select("span.chapter-release-date").text())
            }
        }

        return chapter
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
}
