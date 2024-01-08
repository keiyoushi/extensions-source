package eu.kanade.tachiyomi.extension.en.thepropertyofhate

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import rx.Observable

class ThePropertyOfHate : HttpSource() {
    override val name = "The Property of Hate"

    override val baseUrl = "https://jolleycomics.com"

    override val lang = "en"

    override val supportsLatest = false

    private val firstChapterUrl = "/TPoH/The Hook/"

    // the one and only manga entry
    private val manga: SManga
        get() = SManga.create().apply {
            title = "The Property of Hate"
            thumbnail_url = "https://pbs.twimg.com/media/DOBCcMiWkAA8Hvu.jpg"
            artist = "Sarah Jolley"
            author = "Sarah Jolley"
            status = SManga.UNKNOWN
            url = baseUrl
        }

    override fun fetchPopularManga(page: Int) =
        Observable.just(MangasPage(listOf(manga), false))!!

    // write the data again to avoid bugs in backup restore
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> =
        Observable.just(this.manga.also { it.initialized = true })!!

    // needed for the webview
    override fun mangaDetailsRequest(manga: SManga) = GET(baseUrl, headers)

    // no real base url for this comic so must read the first chapter's link
    override fun chapterListRequest(manga: SManga) =
        GET(baseUrl + firstChapterUrl, headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        val chapters = mutableListOf(
            // must hard code the first one
            SChapter.create().apply {
                url = firstChapterUrl
                chapter_number = 1f
                name = "The Hook"
            },
        )

        document.select("select > option:not(:first-child)")
            .mapIndexed { num, opt ->
                SChapter.create().apply {
                    setUrlWithoutDomain(opt.attr("value"))
                    chapter_number = num + 2f
                    name = opt.text()
                }
            }.let(chapters::addAll)

        return chapters.reversed()
    }

    override fun pageListParse(response: Response) =
        response.asJsoup().select("select > optgroup > option")
            .mapIndexed { num, opt -> Page(num, opt.absUrl("value")) }

    override fun imageUrlParse(response: Response): String =
        response.asJsoup().selectFirst(".comic_comic > img")!!.absUrl("src")

    override fun popularMangaRequest(page: Int): Request =
        throw UnsupportedOperationException("Not used")

    override fun popularMangaParse(response: Response): MangasPage =
        throw UnsupportedOperationException("Not used")

    override fun latestUpdatesParse(response: Response): MangasPage =
        throw UnsupportedOperationException("Not used")

    override fun latestUpdatesRequest(page: Int): Request =
        throw UnsupportedOperationException("Not used")

    override fun mangaDetailsParse(response: Response): SManga =
        throw UnsupportedOperationException("Not used")

    override fun searchMangaParse(response: Response): MangasPage =
        throw UnsupportedOperationException("Not used")

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
        throw UnsupportedOperationException("Search functionality is not available.")
}
