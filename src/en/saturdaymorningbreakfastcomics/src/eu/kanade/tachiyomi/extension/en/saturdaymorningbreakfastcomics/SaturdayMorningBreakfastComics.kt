package eu.kanade.tachiyomi.extension.en.saturdaymorningbreakfastcomics

import android.net.Uri.encode
import eu.kanade.tachiyomi.network.asObservable
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.tryParse
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Split from Hiveworks extension
 */
class SaturdayMorningBreakfastComics : HttpSource() {

    override val name = "Saturday Morning Breakfast Comics"

    override val baseUrl = "https://smbc-comics.com"

    override val lang = "en"

    override val supportsLatest = false

    private fun String.image() =
        "https://fakeimg.ryd.tools/1500x2126/ffffff/000000/?font=museo&font_size=42&text=" + encode(
            this,
        )

    // Taken from XKCD
    private fun wordWrap(text: String) = buildString {
        var charCount = 0
        text.replace("\r\n", " ").split(' ').forEach { w ->
            if (charCount > 25) {
                append("\n")
                charCount = 0
            }
            append(w).append(' ')
            charCount += w.length + 1
        }
    }

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        val manga = SManga.create().apply {
            title = "Saturday Morning Breakfast Comics"
            artist = "Zach Weinersmith"
            author = "Zach Weinersmith"
            status = SManga.ONGOING
            url = "/comic/archive"
            description =
                "SMBC is a daily comic strip about life, philosophy, science, mathematics, and dirty jokes."
            thumbnail_url = "https://fakeimg.ryd.tools/550x780/ffffff/6e7b91/?font=museo&text=SMBC"
        }

        return Observable.just(MangasPage(listOf(manga), false))
    }

    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> = Observable.just(MangasPage(emptyList(), false))

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = Observable.just(manga)

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return client.newCall(chapterListRequest(manga))
            .asObservable()
            .map { response ->
                if (!response.isSuccessful && response.code != 500) {
                    response.close()
                    throw Exception("HTTP ${response.code}")
                }
                response.asJsoup().select("option[value*=\"comic/\"]")
                    .mapIndexed { index, element ->
                        val chapter = SChapter.create()
                        chapter.url = "/${element.attr("value")}"
                        val (date, title) = element.text().split(" - ")
                        chapter.name = title
                        chapter.date_upload = dateFormat.tryParse(date)
                        chapter.chapter_number = (index + 1).toFloat()
                        chapter
                    }
                    .reversed()
            }
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val pages = mutableListOf<Page>()
        val image = document.select("img#cc-comic")
        pages.add(Page(0, "", image.attr("abs:src")))
        if (image.hasAttr("title")) {
            pages.add(Page(1, "", wordWrap(image.attr("title")).image()))
        }
        pages.add(Page(2, "", document.select("#aftercomic > img").attr("abs:src")))
        return pages
    }

    private val dateFormat by lazy {
        SimpleDateFormat("MMMM d, yyyy", Locale.ENGLISH)
    }

    override fun chapterListParse(response: Response): List<SChapter> =
        throw UnsupportedOperationException()

    override fun popularMangaRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun searchMangaParse(response: Response): MangasPage =
        throw UnsupportedOperationException()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
        throw UnsupportedOperationException()

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response): MangasPage =
        throw UnsupportedOperationException()

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun mangaDetailsParse(response: Response): SManga =
        throw UnsupportedOperationException()

    override fun popularMangaParse(response: Response): MangasPage =
        throw UnsupportedOperationException()
}
