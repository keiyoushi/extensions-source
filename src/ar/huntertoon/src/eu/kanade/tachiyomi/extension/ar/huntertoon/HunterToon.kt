package eu.kanade.tachiyomi.extension.ar.huntertoon

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.parseAs
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup

@Source
abstract class HunterToon : HttpSource() {

    override val baseUrl = "https://huntertoon.org"
    override val supportsLatest = true

    private val apiHeaders: Headers by lazy {
        headersBuilder()
            .add("Accept", "application/json, text/plain, */*")
            .add("X-Requested-With", "XMLHttpRequest")
            .add("Referer", "$baseUrl/")
            .build()
    }

    override val client = super.client.newBuilder()
        .rateLimit(2)
        .build()

    // ==================== Popular ====================

    override fun popularMangaRequest(page: Int): Request {
        val offset = (page - 1) * 20
        val url = "$baseUrl/load-more-manhwas".toHttpUrl().newBuilder()
            .addQueryParameter("offset", offset.toString())
            .build()
        return GET(url, apiHeaders)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val data = response.parseAs<LoadMoreResponse>()
        val mangas = data.manhwas.map { it.toSManga() }
        return MangasPage(mangas, data.hasMore)
    }

    // ==================== Latest ====================

    override fun latestUpdatesRequest(page: Int): Request = popularMangaRequest(page)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // ==================== Search ====================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val url = "$baseUrl/search".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .build()
            return GET(url, headers)
        }
        return popularMangaRequest(page)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val doc = Jsoup.parse(response.body.string())
        val mangas = doc.select("a.manhwa-card").map { element ->
            SManga.create().apply {
                url = element.attr("href")
                title = element.selectFirst("h3")?.text() ?: ""
                thumbnail_url = element.selectFirst("img")?.attr("src") ?: ""
            }
        }
        return MangasPage(mangas, mangas.isNotEmpty())
    }

    // ==================== Details ====================

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$baseUrl${manga.url}", headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val doc = Jsoup.parse(response.body.string())

        return SManga.create().apply {
            title = doc.selectFirst(".manhwa-title")?.text() ?: ""
            description = doc.selectFirst("#summary-content")?.text() ?: ""
            thumbnail_url = doc.selectFirst("[data-cover-image]")?.attr("src")
                ?: doc.selectFirst("meta[property='og:image']")?.attr("content")
                ?: ""
            genre = doc.select(".category-tag").joinToString { it.text() }

            val statusText = doc.selectFirst(".status-badge")?.text() ?: ""
            status = when {
                statusText.contains("مستمرة") -> SManga.ONGOING
                statusText.contains("مكتملة") || statusText.contains("منتهية") -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }
    }

    // ==================== Chapters ====================

    override fun chapterListRequest(manga: SManga): Request = GET("$baseUrl${manga.url}", headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val doc = Jsoup.parse(response.body.string())

        return doc.select(".chapter-wrapper").map { element ->
            SChapter.create().apply {
                val chapterNum = element.attr("data-chapter-number")
                name = "الفصل $chapterNum"
                chapter_number = chapterNum.toFloatOrNull() ?: 0f
                url = element.selectFirst("a")?.attr("href") ?: ""
                date_upload = parseRelativeDate(
                    element.selectFirst(".chapter-date")?.text() ?: "",
                )
            }
        }
    }

    // ==================== Pages ====================

    override fun pageListRequest(chapter: SChapter): Request {
        val chapterUrl = if (chapter.url.startsWith("http")) {
            chapter.url
        } else {
            "$baseUrl${chapter.url}"
        }
        return GET(chapterUrl, headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val doc = Jsoup.parse(response.body.string())

        // HunterToon serves chapter images only through its authenticated API
        // The website reader always redirects to an app-only gate page
        throw IllegalStateException(
            "This chapter can only be read in the HunterToon app. " +
                "Download: https://huntertoon.org/app",
        )
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Not used.")

    // ==================== Helpers ====================

    private fun parseRelativeDate(dateStr: String): Long {
        val now = System.currentTimeMillis()
        val num = Regex("(\\d+)").find(dateStr)?.value?.toLongOrNull() ?: 1L
        return when {
            dateStr.contains("دقيقة") || dateStr.contains("دقائق") ->
                now - num * 60 * 1000
            dateStr.contains("ساعة") || dateStr.contains("ساعات") ->
                now - num * 60 * 60 * 1000
            dateStr.contains("يوم") || dateStr.contains("أيام") ->
                now - num * 24 * 60 * 60 * 1000
            dateStr.contains("أسبوع") || dateStr.contains("أسابيع") ->
                now - num * 7 * 24 * 60 * 60 * 1000
            dateStr.contains("شهر") || dateStr.contains("أشهر") ->
                now - num * 30 * 24 * 60 * 60 * 1000
            else -> now
        }
    }

    // ==================== Data Classes ====================

    @Serializable
    private class LoadMoreResponse(
        val manhwas: List<ManhwaDto> = emptyList(),
        @SerialName("has_more") val hasMore: Boolean = false,
    )

    @Serializable
    private class ManhwaDto(
        val id: Int = 0,
        val slug: String = "",
        @SerialName("title_ar") val titleAr: String = "",
        @SerialName("cover_url") val coverUrl: String = "",
        val status: String = "",
        val rating: Double = 0.0,
    ) {
        fun toSManga(): SManga = SManga.create().apply {
            title = this@ManhwaDto.titleAr
            url = "/manhwa/${this@ManhwaDto.slug}"
            thumbnail_url = this@ManhwaDto.coverUrl
        }
    }
}
