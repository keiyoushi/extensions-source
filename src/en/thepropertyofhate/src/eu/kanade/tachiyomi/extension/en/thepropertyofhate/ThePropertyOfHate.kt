package eu.kanade.tachiyomi.extension.en.thepropertyofhate

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup

@Source
abstract class ThePropertyOfHate : KeiSource() {

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

    override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(listOf(manga), false)

    // ========================= Latest =========================

    override suspend fun getLatestUpdates(page: Int) = throw UnsupportedOperationException()

    // ========================= Search =========================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList) = throw UnsupportedOperationException()

    // ========================= Details =========================

    override fun getMangaUrl(manga: SManga): String = baseUrl

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val manga = this.manga.also { it.initialized = true }

        val chapters = if (fetchChapters) {
            getChapterList()
        } else {
            chapters
        }

        return SMangaUpdate(manga, chapters)
    }

    private suspend fun getChapterList(): List<SChapter> {
        val document = client.get("$baseUrl/TPoH/").asJsoup()

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

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(baseUrl + chapter.url).asJsoup()
        .select("select.jumpbox option:not([style*=bold]):not([value=-1])")
        .mapIndexed { num, opt -> Page(num, opt.absUrl("value")) }

    override suspend fun getImageUrl(page: Page): String = client.get(page.url).asJsoup()
        .selectFirst(".comic_comic > img")!!
        .absUrl("src")
}
