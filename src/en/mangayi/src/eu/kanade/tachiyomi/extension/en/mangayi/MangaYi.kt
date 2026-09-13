package eu.kanade.tachiyomi.extension.en.mangayi

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import keiyoushi.utils.tryParseDate
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class MangaYi : KeiSource() {

    override val supportsLatest = false

    override fun OkHttpClient.Builder.configureClient() = rateLimit(2)

    // ============================== Popular ==============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val payload = SearchRequestDto(p = page, t = 1)
        return fetchMangasPage(payload, page)
    }

    // ============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    // ============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val payload = SearchRequestDto(p = page, s = query)
        return fetchMangasPage(payload, page)
    }

    private suspend fun fetchMangasPage(payload: SearchRequestDto, page: Int): MangasPage {
        val response = client.post("$baseUrl/api/search", payload.toJsonRequestBody())
        val dto = response.parseAs<SearchResponseDto>()

        pageSize = maxOf(pageSize, dto.results.size)

        val mangas = dto.results.map { it.toSManga() }
        val hasNextPage = page * pageSize < dto.total
        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Updates ==============================

    override suspend fun fetchMangaUpdate(manga: SManga, chapters: List<SChapter>, fetchDetails: Boolean, fetchChapters: Boolean): SMangaUpdate {
        val document = client.get("$baseUrl/read/${manga.url}/").asJsoup()
        return SMangaUpdate(parseMangaDetails(document), parseChapterList(document))
    }

    private fun parseMangaDetails(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("h1.m-title")!!.text()
        author = document.selectFirst(".m-authors")?.text()
        description = document.select(".m-summary p").joinToString("\n") { it.text() }
        genre = document.select(".m-genres .pill").joinToString { it.text() }
        status = document.selectFirst(".m-stat:contains(Status) .value")?.text().parseStatus()
        thumbnail_url = document.selectFirst(".cover-wrap img.cover-image")?.attr("abs:src")
    }

    private fun parseChapterList(document: Document): List<SChapter> {
        val chapters = document.select("div.chapters-list a.c:not(.unreleased)")
        return chapters.map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                name = element.selectFirst(".t")!!.text()
                date_upload = dateFormat.tryParseDate(element.selectFirst(".chapter-d")?.text())
            }
        }
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
        return document.select("div.c-images img").mapIndexed { index, img ->
            Page(index, imageUrl = img.attr("abs:src"))
        }
    }

    // ============================= Utilities =============================

    private var pageSize = 24

    companion object {
        private val dateFormat = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH).withZone(ZoneId.of("UTC"))
    }
}
