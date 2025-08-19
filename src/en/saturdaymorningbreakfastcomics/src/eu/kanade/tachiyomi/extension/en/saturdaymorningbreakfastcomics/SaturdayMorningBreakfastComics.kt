package eu.kanade.tachiyomi.extension.en.saturdaymorningbreakfastcomics

import android.net.Uri.encode
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

class SaturdayMorningBreakfastComics : ParsedHttpSource() {

    override val name = "Saturday Morning Breakfast Comics"

    override val baseUrl = "https://smbc-comics.com"

    override val lang = "en"

    override val supportsLatest = false

    // Where the archive page is (chapter list is fetched from here)
    private val archiveUrl = "/comic/archive/"

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(::errorIntercept)
        .build()

    private fun String.image() = LATIN_ALT_TEXT_URL + "&text=" + encode(this)

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
            url = archiveUrl
            description = "SMBC is a daily comic strip about life, philosophy, science, mathematics, and dirty jokes."
            thumbnail_url = THUMBNAIL_URL
        }

        return Observable.just(MangasPage(arrayListOf(manga), false))
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

    override fun chapterListSelector() = "option[value*=\"comic/\"]"

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        chapter.url = "/${element.attr("value")}"
        val (date, title) = element.text().split(" - ")
        chapter.name = title
        chapter.date_upload = parseDate(date)

        return chapter
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()

        val image = document.select(IMAGE_SELECTOR)
        pages.add(Page(0, "", image.attr("abs:src")))
        if (image.hasAttr("title")) {
            val text = wordWrap(image.attr("title"))
            pages.add(Page(1, "", text.image()))
        }
        pages.add(Page(2, "", document.select(AFTERCOMIC_SELECTOR).attr("abs:src")))

        return pages
    }

    private fun parseDate(dateStr: String): Long {
        return try {
            dateFormat.parse(dateStr)!!.time
        } catch (_: ParseException) {
            0L
        }
    }

    private val dateFormat by lazy {
        SimpleDateFormat("MMMM d, yyyy", Locale.ENGLISH)
    }

    /**
     *  When fetching the archive page (/comics/archive/), the server responds with a 500
     *  error code alongside HTML code where the list of chapters can be fetched. If we don't catch
     *  the error here, it'll be treated as an any other failed request would; therefore we check
     *  if the archive page specifically is being fetched, and change the error code if that's the case
     */
    private fun errorIntercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        // Only check if the request is for the archive
        if (request.url.toString() != "$baseUrl$archiveUrl") {
            return chain.proceed(request)
        }
        val response: Response = chain.proceed(chain.request())
        if (response.code == 500) {
            return response.newBuilder().code(200).build()
        }
        return response
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()

    override fun popularMangaSelector(): String = throw UnsupportedOperationException()

    override fun searchMangaFromElement(element: Element): SManga = throw UnsupportedOperationException()

    override fun searchMangaNextPageSelector(): String? = throw UnsupportedOperationException()

    override fun searchMangaSelector(): String = throw UnsupportedOperationException()

    override fun popularMangaRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException()

    override fun popularMangaNextPageSelector(): String? = throw UnsupportedOperationException()

    override fun popularMangaFromElement(element: Element): SManga = throw UnsupportedOperationException()

    override fun mangaDetailsParse(document: Document): SManga = throw UnsupportedOperationException()

    override fun latestUpdatesNextPageSelector(): String? = throw UnsupportedOperationException()

    override fun latestUpdatesFromElement(element: Element): SManga = throw UnsupportedOperationException()

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun latestUpdatesSelector(): String = throw UnsupportedOperationException()

    companion object {
        private const val THUMBNAIL_URL =
            "https://fakeimg.ryd.tools/550x780/ffffff/6e7b91/?font=museo&text=SMBC"

        const val LATIN_ALT_TEXT_URL =
            "https://fakeimg.ryd.tools/1500x2126/ffffff/000000/?font=museo&font_size=42"

        const val IMAGE_SELECTOR = "img#cc-comic"

        const val AFTERCOMIC_SELECTOR = "#aftercomic > img"
    }
}
