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

    // the one and only manga entry
    private val manga: SManga
        get() = SManga.create().apply {
            title = "The Property of Hate"
            thumbnail_url = "https://jolleycomics.com/images/Index/tpoh.png"
            artist = "Sarah Jolley"
            author = "Sarah Jolley"
            status = SManga.UNKNOWN
            url = baseUrl
        }

    // ========================= Popular =========================

    override fun fetchPopularManga(page: Int) = Observable.just(MangasPage(listOf(manga), false))!!

    override fun popularMangaRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun popularMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // ========================= Latest =========================

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // ========================= Search =========================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException()

    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // ========================= Details =========================

    // write the data again to avoid bugs in backup restore
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = Observable.just(this.manga.also { it.initialized = true })!!

    // needed for the webview
    override fun mangaDetailsRequest(manga: SManga) = GET(baseUrl, headers)

    override fun mangaDetailsParse(response: Response): SManga = throw UnsupportedOperationException()

    // ========================= Chapters =========================

    override fun chapterListRequest(manga: SManga) = GET("$baseUrl/TPoH/", headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chapters = mutableListOf<SChapter>()
        var addedActiveChapter = false
        var chapterNum = 1f

        val options = document.select("select.jumpbox option:not([value=-1])")
        for (opt in options) {
            val isBold = opt.hasAttr("style") && opt.attr("style").contains("bold")
            if (isBold) {
                val currentChapterNum = chapterNum++
                chapters.add(
                    SChapter.create().apply {
                        setUrlWithoutDomain(opt.absUrl("value"))
                        name = "#${currentChapterNum.toInt()} - ${opt.text().trim()}"
                        chapter_number = currentChapterNum
                    },
                )
            } else {
                if (!addedActiveChapter) {
                    val pageText = opt.text()
                    val chapterName = pageText.substringBefore(" : Page").trim()
                    val pageUrl = opt.attr("value")
                    val chapterUrl = pageUrl.substringBeforeLast("/") + "/"

                    val currentChapterNum = chapterNum++
                    chapters.add(
                        SChapter.create().apply {
                            setUrlWithoutDomain(chapterUrl)
                            name = "#${currentChapterNum.toInt()} - $chapterName"
                            chapter_number = currentChapterNum
                        },
                    )
                    addedActiveChapter = true
                }
            }
        }

        return chapters.reversed()
    }

    // ========================= Pages =========================

    override fun pageListParse(response: Response) = response.asJsoup()
        .select("select.jumpbox option:not([style*=bold]):not([value=-1])")
        .mapIndexed { num, opt -> Page(num, opt.absUrl("value")) }

    override fun imageUrlParse(response: Response): String = response.asJsoup().selectFirst(".comic_comic > img")!!.absUrl("src")
}
