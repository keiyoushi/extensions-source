package eu.kanade.tachiyomi.extension.en.saturdaymorningbreakfastcomics

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.lib.textinterceptor.TextInterceptor
import keiyoushi.lib.textinterceptor.TextInterceptorHelper
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.tryParseDate
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Split from Hiveworks extension
 */
@Source
abstract class SaturdayMorningBreakfastComics : KeiSource() {

    override val supportsLatest = false

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor(TextInterceptor())
        addInterceptor { chain ->
            val request = chain.request()
            val url = request.url
            if (url.host != "thumbnail") return@addInterceptor chain.proceed(request)

            val image = this@SaturdayMorningBreakfastComics::class.java
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
    }

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

    override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(listOf(makeSManga()), false)

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = MangasPage(emptyList(), false)

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        if (!fetchChapters) return SMangaUpdate(makeSManga(), chapters)

        // the archive page responds with HTTP 500 despite serving the full list
        val response = client.get(getMangaUrl(manga), ensureSuccess = false)
        if (!response.isSuccessful && response.code != 500) {
            response.close()
            throw Exception("HTTP ${response.code}")
        }

        val chapterList = response.asJsoup().select("option[value*=\"comic/\"]")
            .mapIndexed { index, element ->
                val chapter = SChapter.create()
                chapter.url = "/${element.attr("value")}"
                val (date, title) = element.text().split(" - ")
                chapter.name = title
                chapter.date_upload = dateFormat.tryParseDate(date)
                chapter.chapter_number = (index + 1).toFloat()
                chapter
            }
            .reversed()

        return SMangaUpdate(makeSManga(), chapterList)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        val pages = mutableListOf<Page>()
        val image = document.select("img#cc-comic")
        pages.add(Page(0, "", image.attr("abs:src")))
        if (image.hasAttr("title")) {
            pages.add(Page(1, "", TextInterceptorHelper.createUrl("", image.attr("title"))))
        }
        pages.add(Page(2, "", document.select("#aftercomic > img").attr("abs:src")))
        return pages
    }
}

private val dateFormat = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH)
