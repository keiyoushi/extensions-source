package eu.kanade.tachiyomi.extension.en.mangayi

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import keiyoushi.utils.tryParse
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Source
abstract class MangaYi : HttpSource() {

    override val supportsLatest = false

    override val client = network.client.newBuilder()
        .rateLimit(2)
        .build()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ============================== Popular ==============================

    override fun popularMangaRequest(page: Int): Request {
        val payload = SearchRequestDto(t = 1)
        return POST(
            url = "$baseUrl/api/search",
            headers = headers.newBuilder()
                .add("Accept", "application/json")
                .build(),
            body = payload.toJsonRequestBody(),
        )
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val mangas = response.parseAs<List<MangaDto>>().map { it.toSManga() }
        return MangasPage(mangas, false)
    }

    // ============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // ============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val payload = SearchRequestDto(s = query)
        return POST(
            url = "$baseUrl/api/search",
            headers = headers.newBuilder()
                .add("Accept", "application/json")
                .build(),
            body = payload.toJsonRequestBody(),
        )
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // ============================== Details ==============================

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$baseUrl/read/${manga.url}/", headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("h1.title")!!.text()
            author = document.selectFirst(".authors")?.text()
            description = document.select(".summary p").joinToString("\n") { it.text() }
            genre = document.select(".genres .pill").joinToString { it.text() }
            status = document.selectFirst(".stat:contains(Status) .value")?.text().parseStatus()
            thumbnail_url = document.selectFirst(".cover-wrapper img.cover-image")?.attr("abs:src")
        }
    }

    private fun String?.parseStatus(): Int = when (this?.lowercase()) {
        "ongoing" -> SManga.ONGOING
        "completed" -> SManga.COMPLETED
        "hiatus", "on hiatus" -> SManga.ON_HIATUS
        "cancelled" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    // ============================= Chapters ==============================

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chapters = document.select("div.chapters a.c:not(.unreleased)")

        if (chapters.isEmpty()) return emptyList()

        return chapters.map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                name = element.selectFirst(".t")!!.text()
                date_upload = dateFormat.tryParse(element.selectFirst(".chapter-date")?.text())
            }
        }
    }

    // =============================== Pages ===============================

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("div.images img").mapIndexed { index, img ->
            Page(index, imageUrl = img.attr("abs:src"))
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================= Utilities =============================

    companion object {
        private val dateFormat = SimpleDateFormat("d MMMM yyyy", Locale.ENGLISH).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }
}
