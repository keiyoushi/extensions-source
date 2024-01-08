package eu.kanade.tachiyomi.extension.en.dynasty

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class DynastyDoujins : DynastyScans() {

    override val name = "Dynasty-Doujins"

    override val searchPrefix = "doujins"

    override fun popularMangaInitialUrl() = "$baseUrl/doujins?view=cover"

    override fun popularMangaFromElement(element: Element): SManga {
        return super.popularMangaFromElement(element).apply {
            thumbnail_url = element.select("img").attr("abs:src").let {
                if (it.contains("cover_missing")) {
                    null
                } else {
                    it
                }
            }
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/search?q=$query&classes%5B%5D=Doujin&sort=&page=$page", headers)
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create().apply {
            title = document.selectFirst("div#main > h2 > b")!!.text().substringAfter("Doujins › ")
            description = document.select("div#main > div.description").text()
            thumbnail_url = document.select("a.thumbnail img").firstOrNull()?.attr("abs:src")
                ?.replace("/thumb/", "/medium/")
        }
        parseGenres(document, manga)
        return manga
    }

    override fun chapterListSelector() = "div#main > dl.chapter-list > dd"

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        val chapters = document.select(chapterListSelector()).map { chapterFromElement(it) }.toMutableList()

        document.select("a.thumbnail img").let { images ->
            if (images.isNotEmpty()) {
                chapters.add(
                    SChapter.create().apply {
                        name = "Images"
                        setUrlWithoutDomain(document.location() + "/images")
                    },
                )
            }
        }

        return chapters
    }

    override fun pageListParse(document: Document): List<Page> {
        return if (document.location().endsWith("/images")) {
            document.select("a.thumbnail").mapIndexed { i, element ->
                Page(i, element.attr("abs:href"))
            }
        } else {
            super.pageListParse(document)
        }
    }

    override fun imageUrlParse(document: Document): String {
        return document.select("div.image img").attr("abs:src")
    }
}
