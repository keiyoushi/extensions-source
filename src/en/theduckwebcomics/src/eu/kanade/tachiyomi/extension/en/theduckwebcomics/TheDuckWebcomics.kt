package eu.kanade.tachiyomi.extension.en.theduckwebcomics

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class TheDuckWebcomics : ParsedHttpSource() {
    override val name = "The Duck Webcomics"

    override val baseUrl = "https://www.theduckwebcomics.com"

    override val lang = "en"

    override val supportsLatest = true

    override fun latestUpdatesSelector() = searchMangaSelector()

    override fun latestUpdatesNextPageSelector() = searchMangaNextPageSelector()

    override fun latestUpdatesRequest(page: Int) =
        GET("$baseUrl/search/?page=$page&last_update=today", headers)

    override fun latestUpdatesFromElement(element: Element) =
        searchMangaFromElement(element)

    override fun popularMangaSelector() = searchMangaSelector()

    override fun popularMangaNextPageSelector() = searchMangaNextPageSelector()

    override fun popularMangaRequest(page: Int) =
        GET("$baseUrl/search/?page=$page", headers)

    override fun popularMangaFromElement(element: Element) =
        searchMangaFromElement(element)

    override fun searchMangaSelector() = ".breadcrumb ~ div[style]"

    override fun searchMangaNextPageSelector() = "a.next"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) =
        "$baseUrl/search".toHttpUrl().newBuilder().run {
            addQueryParameter("search", query)
            addQueryParameter("page", page.toString())
            filters.forEach { (it as QueryParam).encode(this) }
            GET(build(), headers)
        }

    override fun searchMangaFromElement(element: Element) =
        SManga.create().apply {
            element.selectFirst(".size24")!!.let {
                title = it.text()
                url = it.attr("href")
            }
            genre = element.selectFirst(".size10")!!.text().substringBefore(",")
            description = element.selectFirst(".comicdescparagraphs")!!.text()
            thumbnail_url = element.selectFirst("img")!!.absUrl("src")
            author = element.selectFirst(".size18")!!.text()
            artist = author
        }

    // The details are only available in search
    override fun fetchMangaDetails(manga: SManga) =
        rx.Observable.just(manga.apply { initialized = true })!!

    override fun chapterListSelector() = "#page_dropdown > option"

    override fun chapterListParse(response: Response) =
        response.asJsoup().run {
            selectFirst(".yellow-box > .paranomargin")?.text()?.let(::error)
            select(chapterListSelector()).mapIndexed { idx, el ->
                SChapter.create().apply {
                    chapter_number = idx + 1f
                    name = el.text().substringAfter("- ")
                    setUrlWithoutDomain(el.absUrl("value") + '/')
                }
            }
        }

    override fun pageListParse(document: Document) =
        listOf(Page(0, "", document.selectFirst(".page-image")!!.absUrl("src")))

    override fun mangaDetailsParse(document: Document) =
        throw UnsupportedOperationException("Not used")

    override fun chapterFromElement(element: Element) =
        throw UnsupportedOperationException("Not used")

    override fun imageUrlParse(document: Document) =
        throw UnsupportedOperationException("Not used")

    override fun getFilterList() = FilterList(
        TypeFilter(),
        ToneFilter(),
        StyleFilter(),
        GenreFilter(),
        RatingFilter(),
        UpdateFilter(),
    )
}
