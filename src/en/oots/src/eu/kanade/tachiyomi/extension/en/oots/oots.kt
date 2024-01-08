package eu.kanade.tachiyomi.extension.en.oots

import android.app.Application
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class oots : ParsedHttpSource() {
    override val name = "The Order Of The Stick (OOTS)"

    override val baseUrl = "https://www.giantitp.com"

    override val lang = "en"

    override val supportsLatest = false

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        val manga = SManga.create().apply {
            title = "The Order Of The Stick"
            artist = "Rich Burlew"
            author = "Rich Burlew"
            status = SManga.ONGOING
            url = "/comics/oots.html"
            description = "Having fun with games."
            thumbnail_url = "https://i.giantitp.com/redesign/Icon_Comics_OOTS.gif"
        }

        manga.initialized = true
        return Observable.just(MangasPage(listOf(manga), false))
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = Observable.just(MangasPage(emptyList(), false))

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = Observable.just(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapterList = super.chapterListParse(response).distinct()
        return chapterList.mapIndexed {
                i, ch ->
            ch.apply { chapter_number = chapterList.size.toFloat() - i }
        }
    }

    override fun chapterListSelector() = "p.ComicList a"

    override fun chapterFromElement(element: Element): SChapter {
        val seriesPrefs = Injekt.get<Application>().getSharedPreferences("source_${id}_time_found", 0)
        val seriesPrefsEditor = seriesPrefs.edit()

        val chapter = SChapter.create()
        chapter.url = element.attr("href")
        chapter.name = element.text()

        val numberRegex = """oots(\d+)\.html""".toRegex()
        val number = numberRegex.find(chapter.url)!!.groupValues[1]

        // Save current time when a chapter is found for the first time, and reuse it on future checks to
        // prevent manga entry without any new chapter bumped to the top of "Latest chapter" list
        // when the library is updated.
        val currentTimeMillis = System.currentTimeMillis()
        if (!seriesPrefs.contains(number)) {
            seriesPrefsEditor.putLong(number, currentTimeMillis)
        }

        chapter.date_upload = seriesPrefs.getLong(number, currentTimeMillis)

        seriesPrefsEditor.apply()

        return chapter
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()

        fun addPage(document: Document) {
            pages.add(Page(pages.size, "", document.select("td[align='center'] > img").attr("src")))
        }

        addPage(document)

        return pages
    }

    override fun imageUrlParse(document: Document) = throw Exception("Not used")

    override fun popularMangaSelector(): String = throw Exception("Not used")

    override fun searchMangaFromElement(element: Element): SManga = throw Exception("Not used")

    override fun searchMangaNextPageSelector(): String? = throw Exception("Not used")

    override fun searchMangaSelector(): String = throw Exception("Not used")

    override fun popularMangaRequest(page: Int): Request = throw Exception("Not used")

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw Exception("Not used")

    override fun popularMangaNextPageSelector(): String? = throw Exception("Not used")

    override fun popularMangaFromElement(element: Element): SManga = throw Exception("Not used")

    override fun mangaDetailsParse(document: Document): SManga = throw Exception("Not used")

    override fun latestUpdatesNextPageSelector(): String? = throw Exception("Not used")

    override fun latestUpdatesFromElement(element: Element): SManga = throw Exception("Not used")

    override fun latestUpdatesRequest(page: Int): Request = throw Exception("Not used")

    override fun latestUpdatesSelector(): String = throw Exception("Not used")
}
