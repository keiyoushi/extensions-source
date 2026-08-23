package eu.kanade.tachiyomi.extension.en.mangayi

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import keiyoushi.utils.tryParseDate
import okhttp3.OkHttpClient
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class MangaYi : KeiSource() {

    override val supportsLatest = false

    override fun OkHttpClient.Builder.configureClient() = rateLimit(2)

    // ============================== Popular ==============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val payload = SearchRequestDto(t = 1)
        val mangas = response.parseAs<List<MangaDto>>().map { it.toSManga() }
        return MangasPage(mangas, false)
    }

    // ============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    // ============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val payload = SearchRequestDto(s = query)
        val mangas = response.parseAs<List<MangaDto>>().map { it.toSManga() }
        return MangasPage(mangas, false)
    }

    // ============================== Updates ==============================

    override suspend fun fetchMangaUpdate(manga: SManga, chapters: List<SChapter>, fetchDetails: Boolean, fetchChapters: Boolean): SMangaUpdate {
        val document = client.get("$baseUrl/read/${manga.url}/").asJsoup()
        val manga2 = SManga.create().apply {
            title = document.selectFirst("h1.title")!!.text()
            author = document.selectFirst(".authors")?.text()
            description = document.select(".summary p").joinToString("\n") { it.text() }
            genre = document.select(".genres .pill").joinToString { it.text() }
            status = document.selectFirst(".stat:contains(Status) .value")?.text().parseStatus()
            thumbnail_url = document.selectFirst(".cover-wrapper img.cover-image")?.attr("abs:src")
        }

        val chapters = document.select("div.chapters a.c:not(.unreleased)")
        val chapters2 = chapters.map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                name = element.selectFirst(".t")!!.text()
                date_upload = dateFormat.tryParseDate(element.selectFirst(".chapter-date")?.text())
            }
        }

        return SMangaUpdate(manga2, chapters2)
    }

    private fun String?.parseStatus(): Int = when (this?.lowercase()) {
        "ongoing" -> SManga.ONGOING
        "completed" -> SManga.COMPLETED
        "hiatus", "on hiatus" -> SManga.ON_HIATUS
        "cancelled" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    // =============================== Pages ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        return document.select("div.images img").mapIndexed { index, img ->
            Page(index, imageUrl = img.attr("abs:src"))
        }
    }

    // ============================= Utilities =============================

    companion object {
        private val dateFormat = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH).withZone(ZoneId.of("UTC"))
    }
}
