package eu.kanade.tachiyomi.extension.en.sacachispa

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response

@Source
abstract class Sacachispa : HttpSource() {

    override val supportsLatest = false

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ============================== Popular ==============================

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/api/series?page=$page&pageSize=24", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val dto = response.parseAs<SeriesResponseDto>()
        val mangas = dto.items.map { it.toSManga() }
        val hasNext = dto.page < dto.totalPages
        return MangasPage(mangas, hasNext)
    }

    // ============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // ============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/api/series".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("pageSize", "24")
            .addQueryParameter("search", query)
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // ============================== Details ==============================

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$baseUrl/series/${manga.url}", headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("h1.font-heading")!!.text()
            description = document.selectFirst("p.max-w-2xl")?.text()
            author = document.selectFirst("span:contains(Author:)")?.text()?.substringAfter("Author: ")
            artist = document.selectFirst("span:contains(Artist:)")?.text()?.substringAfter("Artist: ")

            val badges = document.select("span[data-slot=badge]")
            val statusText = badges.map { it.text().lowercase() }.firstOrNull { it == "ongoing" || it == "completed" }
            status = parseStatus(statusText)

            genre = document.select("a[href^=/browse?genre=] span").joinToString { it.text() }
            thumbnail_url = document.selectFirst("div.aspect-\\[2\\/3\\] img")?.absUrl("src")
        }
    }

    private fun parseStatus(status: String?): Int = when (status?.lowercase()) {
        "ongoing" -> SManga.ONGOING
        "completed" -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // ============================= Chapters ==============================

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val dto = response.extractNextJs<RscChaptersDto> {
            it is JsonObject && "chapters" in it
        } ?: return emptyList()

        val slug = response.request.url.pathSegments.last { it.isNotEmpty() }

        return dto.chapters
            .map { it.toSChapter(slug) }
            .sortedByDescending { it.chapter_number }
    }

    // =============================== Pages ===============================

    override fun pageListParse(response: Response): List<Page> {
        val dto = response.extractNextJs<RscPageDto> { element ->
            if (element !is JsonObject) return@extractNextJs false
            val chapter = element["chapter"] as? JsonObject
            chapter != null && "pages" in chapter
        } ?: return emptyList()

        return dto.chapter.toPageList()
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
