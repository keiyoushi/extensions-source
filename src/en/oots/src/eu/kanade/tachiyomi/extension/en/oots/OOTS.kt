package eu.kanade.tachiyomi.extension.en.oots

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferencesLazy
import okhttp3.Request
import okhttp3.Response
import rx.Observable

class OOTS : HttpSource() {
    override val name = "The Order Of The Stick (OOTS)"

    override val baseUrl = "https://www.giantitp.com"

    override val lang = "en"

    override val supportsLatest = false

    private val preferences by getPreferencesLazy()

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        val manga = SManga.create().apply {
            title = "The Order Of The Stick"
            artist = "Rich Burlew"
            author = "Rich Burlew"
            status = SManga.ONGOING
            url = "/comics/oots.html"
            description = "Having fun with games."
            thumbnail_url = "https://i.giantitp.com/redesign/Icon_Comics_OOTS.gif"
            initialized = true
        }

        return Observable.just(MangasPage(listOf(manga), false))
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = Observable.just(MangasPage(emptyList(), false))

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = Observable.just(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val elements = document.select("p.ComicList a")

        val currentTimeMillis = System.currentTimeMillis()
        val prefs = preferences
        val editor = prefs.edit()

        val chapters = elements.map { element ->
            SChapter.create().apply {
                url = element.attr("href")
                name = element.text()

                val numberMatch = NUMBER_REGEX.find(url)
                val numberStr = numberMatch?.groupValues?.get(1) ?: ""
                chapter_number = numberStr.toFloatOrNull() ?: -1f

                if (numberStr.isNotEmpty()) {
                    if (!prefs.contains(numberStr)) {
                        editor.putLong(numberStr, currentTimeMillis)
                    }
                    date_upload = prefs.getLong(numberStr, currentTimeMillis)
                } else {
                    date_upload = currentTimeMillis
                }
            }
        }.distinctBy { it.url }

        editor.apply()

        return chapters.reversed()
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val imageUrl = document.select("td[align='center'] > img").attr("abs:src")
        return listOf(Page(0, imageUrl = imageUrl))
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun popularMangaRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun popularMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException()

    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun mangaDetailsParse(response: Response): SManga = throw UnsupportedOperationException()

    companion object {
        private val NUMBER_REGEX = """oots(\d+)\.html""".toRegex()
    }
}
