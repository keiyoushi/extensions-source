package eu.kanade.tachiyomi.extension.en.battleinfivesecondsaftermeeting

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Response
import rx.Observable

class BattleInFiveSecondsAfterMeeting : Madara(
    "Battle In 5 Seconds After Meeting",
    "https://www.deatte5.com",
    "en",
) {
    override val supportsLatest = false
    override val fetchGenres = false

    override val mangaDetailsSelectorTitle = "h1"
    override val mangaDetailsSelectorAuthor = "h5:contains(Author) + h4 a"
    override val mangaDetailsSelectorArtist = "h5:contains(Artist) + h4 a"
    override val mangaDetailsSelectorDescription = ".synopsis p"
    override val mangaDetailsSelectorThumbnail = ".cover_managa img"
    override val mangaDetailsSelectorStatus = "h5:contains(Status) + h4"
    override val mangaDetailsSelectorTag = "h5:contains(Tag) + h4 a"
    override val seriesTypeSelector = "h5:contains(Type) + h4"
    override val altNameSelector = "h5:contains(Alternative) + h4"

    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> {
        return fetchPopularManga(page)
    }

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        val manga = SManga.create().apply {
            setUrlWithoutDomain(baseUrl)
            title = "Battle in 5 Seconds After Meeting Manga"
            thumbnail_url = "$baseUrl/wp-content/uploads/2022/01/48.jpg"
        }

        return Observable.just(MangasPage(listOf(manga), false))
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        launchIO { countViews(document) }

        val chapterElements = document.select(".main-chapter")
        val recentChapters = document.select(chapterListSelector()).map(::chapterFromElement)

        return chapterElements.map { element ->
            SChapter.create().apply {
                val chapterContent = element.selectFirst(".chapter-content")!!.text()
                setUrlWithoutDomain(element.selectFirst("a")!!.attr("abs:href"))
                name = chapterContent.removePrefix("Battle in 5 Seconds After Meeting, ")

                val otherChapter = recentChapters.find { it.name == chapterContent }
                if (otherChapter != null) {
                    date_upload = otherChapter.date_upload
                }
            }
        }
    }

    override fun getFilterList(): FilterList {
        return FilterList()
    }
}
