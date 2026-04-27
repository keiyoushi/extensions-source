package eu.kanade.tachiyomi.extension.en.theduckwebcomics

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import org.jsoup.nodes.Element
import rx.Observable

class TheDuckWebcomics : HttpSource() {
    override val name = "The Duck Webcomics"

    override val baseUrl = "https://www.theduckwebcomics.com"

    override val lang = "en"

    override val supportsLatest = true

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/search/?page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".breadcrumb ~ div[style]").map { element ->
            searchMangaFromElement(element)
        }
        val hasNextPage = document.selectFirst("a.next") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/search/?page=$page&last_update=today", headers)

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = "$baseUrl/search".toHttpUrl().newBuilder().run {
        addQueryParameter("search", query)
        addQueryParameter("page", page.toString())
        filters.filterIsInstance<QueryParam>().forEach { it.encode(this) }
        GET(build(), headers)
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    private fun searchMangaFromElement(element: Element) = SManga.create().apply {
        val titleEl = element.selectFirst(".size24") ?: throw Exception("Title element not found")
        title = titleEl.text()
        setUrlWithoutDomain(titleEl.absUrl("href"))

        genre = element.selectFirst(".size10")?.text()?.substringBefore(",")
        description = element.selectFirst(".comicdescparagraphs")?.text()
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
        author = element.selectFirst(".size18")?.text()
        artist = author
    }

    // The details are only available in search
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = Observable.just(manga.apply { initialized = true })

    override fun mangaDetailsParse(response: Response) = throw UnsupportedOperationException()

    override fun chapterListParse(response: Response): List<SChapter> = response.asJsoup().run {
        selectFirst(".yellow-box > .paranomargin")?.text()?.let(::error)
        select("#page_dropdown > option").mapIndexed { idx, el ->
            SChapter.create().apply {
                chapter_number = idx + 1f
                name = el.text().substringAfter("- ")
                setUrlWithoutDomain(el.absUrl("value") + "/")
            }
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val imageUrl = document.selectFirst(".page-image")?.absUrl("src")
            ?: throw Exception("Page image not found")
        return listOf(Page(0, imageUrl = imageUrl))
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    override fun getFilterList() = FilterList(
        TypeFilter(),
        ToneFilter(),
        StyleFilter(),
        GenreFilter(),
        RatingFilter(),
        UpdateFilter(),
    )
}
