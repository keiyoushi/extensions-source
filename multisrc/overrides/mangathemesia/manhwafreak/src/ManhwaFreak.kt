package eu.kanade.tachiyomi.extension.en.manhwafreak

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Request
import org.jsoup.nodes.Element
import java.util.Calendar

class ManhwaFreak : MangaThemesia("Manhwa Freak", "https://manhwa-freak.com", "en") {

    // they called the theme "mangareaderfix"

    // popular
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl$mangaUrlDirectory?order=views", headers)

    // latest
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl$mangaUrlDirectory", headers)

    // search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
        GET("$baseUrl/page/$page/?s=$query")

    override fun searchMangaSelector() = ".listupd .lastest-serie"

    // manga details
    override val seriesDetailsSelector = ".wrapper .series"

    override val seriesThumbnailSelector = ".info img"
    override val seriesTitleSelector = "h1.title"
    override val seriesArtistSelector = "#info div:contains(Artist) > p:last-child"
    override val seriesAuthorSelector = "#info div:contains(Author) > p:last-child"
    override val seriesStatusSelector = "#info div:contains(Status) > p:last-child"
    override val seriesDescriptionSelector = "#summary"
    override val seriesGenreSelector = "#info div:contains(Genre) > p:last-child"

    override val seriesAltNameSelector = "#info div:contains(Alternative) > p:last-child"
    override val seriesTypeSelector = "#info div:contains(Type) > p:last-child"

    override fun String?.parseStatus(): Int = when {
        this == null -> SManga.UNKNOWN
        listOf("ongoing", "publishing", "release").any { this.contains(it, ignoreCase = true) } -> SManga.ONGOING
        this.contains("completed", ignoreCase = true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // chapter list
    override fun chapterListSelector() = ".chapter-li a:not(:has(svg))"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        val urlElements = element.select("a")
        setUrlWithoutDomain(urlElements.attr("href"))
        val chapterElements = element.select(".chapter-info")
        name = chapterElements.select("p:nth-child(1)").text().ifBlank { urlElements.first()!!.text() }
        date_upload = getChapterDate(chapterElements.first())
    }

    override fun getFilterList() = FilterList()

    private fun getChapterDate(element: Element?): Long {
        element ?: return 0
        val chapterDate = element.select("p:nth-child(2)").text()

        return when {
            element.select("p.new").isNotEmpty() -> getToday()
            chapterDate.contains(Regex("day(s)* ago$")) -> {
                val number = Regex("""(\d+)""").find(chapterDate)?.value?.toIntOrNull() ?: return 0
                Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -number) }.timeInMillis
            }

            else -> chapterDate.parseChapterDate()
        }
    }

    private fun getToday(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
