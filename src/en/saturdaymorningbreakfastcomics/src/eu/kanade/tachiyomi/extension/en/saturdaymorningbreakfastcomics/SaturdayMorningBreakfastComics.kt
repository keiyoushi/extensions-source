package eu.kanade.tachiyomi.extension.en.saturdaymorningbreakfastcomics

import eu.kanade.tachiyomi.lib.textinterceptor.TextInterceptor
import eu.kanade.tachiyomi.lib.textinterceptor.TextInterceptorHelper
import eu.kanade.tachiyomi.network.asObservable
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.tryParse
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
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
    override val client: OkHttpClient = network.client.newBuilder()
        .addInterceptor(TextInterceptor())
        .addInterceptor { chain ->
            val request = chain.request()
            val url = request.url
            if (url.host != "thumbnail") return@addInterceptor chain.proceed(request)

            val image = this::class.java
                .getResourceAsStream("/assets/thumbnail.png")!!
                .readBytes()
            val responseBody = image.toResponseBody("image/png".toMediaType())
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(responseBody)
                .build()
        }
        .build()

    private fun makeSManga(): SManga = SManga.create().apply {
        title = "Saturday Morning Breakfast Comics"
        artist = "Zach Weinersmith"
        author = "Zach Weinersmith"
        status = SManga.ONGOING
        url = "/comic/archive"
        description =
            "SMBC is a daily comic strip about life, philosophy, science, mathematics, and dirty jokes."
        thumbnail_url = "https://thumbnail/smbc.png"
    }

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        val manga = makeSManga()
        return Observable.just(MangasPage(listOf(manga), false))
    }

    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> = Observable.just(MangasPage(emptyList(), false))

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = Observable.just(makeSManga())

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = client.newCall(chapterListRequest(manga))
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

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val pages = mutableListOf<Page>()
        val image = document.select("img#cc-comic")
        pages.add(Page(0, "", image.attr("abs:src")))
        if (image.hasAttr("title")) {
            pages.add(Page(1, "", TextInterceptorHelper.createUrl("", image.attr("title"))))
        }
        pages.add(Page(2, "", document.select("#aftercomic > img").attr("abs:src")))
        return pages
    }

    private val dateFormat by lazy {
        SimpleDateFormat("MMMM d, yyyy", Locale.ENGLISH)
    }

    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()

    override fun popularMangaRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException()

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun mangaDetailsParse(response: Response): SManga = throw UnsupportedOperationException()

    override fun popularMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()
}
