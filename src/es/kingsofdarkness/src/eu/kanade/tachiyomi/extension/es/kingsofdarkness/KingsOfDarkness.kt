package eu.kanade.tachiyomi.extension.es.kingsofdarkness

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class KingsOfDarkness : ParsedHttpSource() {
    override val name = "Kings Of Darkness"

    override val baseUrl = "https://kings-of-darkness.wixsite.com/0000"

    override val lang = "es"

    override val supportsLatest = false

    override fun popularMangaSelector() = "#SITE_PAGES div.wixui-image"

    override fun popularMangaRequest(page: Int) =
        GET("$baseUrl/proyectos", headers)

    override fun popularMangaFromElement(element: Element) =
        SManga.create().apply {
            url = element.child(0).attr("href")
            title = element.nextElementSibling()!!.text()
            thumbnail_url = element.selectFirst("img")!!.image
        }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList) =
        fetchPopularManga(page).map { mp ->
            mp.copy(mp.mangas.filter { it.title.contains(query, true) })
        }!!

    override fun mangaDetailsRequest(manga: SManga) =
        GET(manga.url, headers)

    override fun mangaDetailsParse(document: Document) =
        SManga.create().apply {
            url = document.location()
            title = document.selectFirst("#SITE_PAGES h2")!!.text()
            thumbnail_url = document.selectFirst("#SITE_PAGES img")!!.image
            document.select("#SITE_PAGES p:last-of-type").let { el ->
                description = el[0].text().trim()
                genre = el[1].select("a").joinToString { it.text() }
            }
        }

    override fun chapterListSelector() = "#SITE_PAGES a[target=_self]"

    override fun chapterListRequest(manga: SManga) =
        GET(manga.url, headers)

    override fun chapterFromElement(element: Element) =
        SChapter.create().apply {
            url = element.attr("href")
            name = element.child(0).text()
            chapter_number = name.substring(3).toFloat()
        }

    override fun pageListRequest(chapter: SChapter) =
        GET(chapter.url, headers)

    override fun pageListParse(document: Document) =
        document.select("#SITE_PAGES img").mapIndexed { idx, el ->
            Page(idx, "", el.image)
        }

    private inline val Element.image: String
        get() = attr("src").substringBefore("/v1/fill")

    override fun latestUpdatesSelector() = ""

    override fun latestUpdatesNextPageSelector(): String? = null

    override fun latestUpdatesRequest(page: Int) =
        throw UnsupportedOperationException("Not used!")

    override fun latestUpdatesFromElement(element: Element) =
        throw UnsupportedOperationException("Not used!")

    override fun popularMangaNextPageSelector(): String? = null

    override fun searchMangaSelector() = ""

    override fun searchMangaNextPageSelector(): String? = null

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) =
        throw UnsupportedOperationException("Not used!")

    override fun searchMangaFromElement(element: Element) =
        throw UnsupportedOperationException("Not used!")

    override fun imageUrlParse(document: Document) =
        throw UnsupportedOperationException("Not used!")
}
