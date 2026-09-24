package eu.kanade.tachiyomi.extension.id.pramramadhan

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class Pramramadhan : KeiSource() {

    // ============================== Popular ===============================
    override suspend fun getPopularManga(page: Int): MangasPage {
        if (page > 1) return MangasPage(emptyList(), false)

        val document = client.get("$baseUrl/search.php?sort=popular&page=$page").asJsoup()
        return mangaListParse(document)
    }

    // ============================== Latest ================================
    override suspend fun getLatestUpdates(page: Int): MangasPage {
        if (page > 1) return MangasPage(emptyList(), false)

        val document = client.get("$baseUrl/search.php?sort=newest&page=$page").asJsoup()
        return mangaListParse(document)
    }

    // ============================== Search ================================
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (page > 1) return MangasPage(emptyList(), false)

        val url = "$baseUrl/search.php".toHttpUrl().newBuilder().apply {
            if (query.isNotEmpty()) {
                addQueryParameter("q", query)
            }
            addQueryParameter("page", page.toString())

            filters.forEach { filter ->
                when (filter) {
                    is SortFilter -> addQueryParameter("sort", filter.toUriPart())
                    is GenreFilter -> if (filter.toUriPart().isNotEmpty()) addQueryParameter("genre", filter.toUriPart())
                    is FormatFilter -> if (filter.toUriPart().isNotEmpty()) addQueryParameter("type", filter.toUriPart())
                    is ProjectFilter -> if (filter.toUriPart().isNotEmpty()) addQueryParameter("project", filter.toUriPart())
                    is StatusFilter -> if (filter.toUriPart().isNotEmpty()) addQueryParameter("status", filter.toUriPart())
                    is AuthorFilter -> if (filter.state.isNotEmpty()) addQueryParameter("author", filter.state)
                    is ArtistFilter -> if (filter.state.isNotEmpty()) addQueryParameter("artist", filter.state)
                    else -> {}
                }
            }
        }.build()

        val document = client.get(url).asJsoup()
        return mangaListParse(document)
    }

    private fun mangaListParse(document: Document): MangasPage {
        val mangas = document.select("a.result-card").mapNotNull { element ->
            val titleText = element.selectFirst("div.result-title")?.text() ?: return@mapNotNull null
            SManga.create().apply {
                setUrlWithoutDomain(element.attr("abs:href"))
                title = titleText
                thumbnail_url = element.selectFirst("div.result-cover img")?.attr("abs:src")
            }
        }
        return MangasPage(mangas, false)
    }

    // =========================== Manga Details & Updates ==================
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        return SMangaUpdate(
            mangaDetailsParse(document).apply { url = manga.url },
            chapterListParse(document),
        )
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (!url.host.equals(baseUrl.toHttpUrl().host, ignoreCase = true) && !url.host.equals("pramramadhan.my.id", ignoreCase = true)) return null

        val segments = url.pathSegments
        if (segments.size < 2 || segments[0] != "series") return null

        val slug = segments[1]
        if (slug.isEmpty()) return null

        val mangaUrl = "/series/$slug"
        val document = client.get(getMangaUrl(SManga.create().apply { this.url = mangaUrl })).asJsoup()
        if (document.selectFirst("h1.series-title") == null) return null

        return mangaDetailsParse(document).apply {
            this.url = mangaUrl
        }
    }

    private fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        val titleText = document.selectFirst("h1.series-title")?.text()
            ?: throw Exception("Missing title")
        title = titleText
        author = document.selectFirst(".tag-row:has(.tag-label:contains(Author)) .tag-pill")?.text()
        artist = document.selectFirst(".tag-row:has(.tag-label:contains(Artist)) .tag-pill")?.text()
        genre = document.select(".tag-row:has(.tag-label:contains(Genre)) .tag-list a.tag-pill")
            .joinToString { it.text() }
        status = parseStatus(document.selectFirst(".tag-row:has(.tag-label:contains(Status)) .tag-pill")?.text())
        description = document.selectFirst("p.series-desc")?.text()
        thumbnail_url = document.selectFirst("div.series-cover img")?.attr("abs:src")
        initialized = true
    }

    private fun parseStatus(status: String?): Int = when (status?.lowercase()) {
        "ongoing" -> SManga.ONGOING
        "completed" -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // ============================== Chapters ==============================
    private fun chapterListParse(document: Document): List<SChapter> = document.select("div.chapter-grid a.chapter-card").map { element ->
        val titleText = element.selectFirst("div.chapter-title")?.text()
            ?: throw Exception("Missing chapter name")
        SChapter.create().apply {
            setUrlWithoutDomain(element.attr("abs:href"))
            name = buildString {
                append(titleText)
                val subTitle = element.selectFirst("div.chapter-sub")?.text()
                if (!subTitle.isNullOrEmpty()) {
                    append(" - ")
                    append(subTitle)
                }
            }
            date_upload = parseChapterDate(element.selectFirst("div.chapter-time")?.text())
        }
    }

    private fun parseChapterDate(dateString: String?): Long {
        if (dateString == null) return 0L
        val trimmedDate = dateString.lowercase().trim().removeSuffix("lalu").trim()

        return runCatching {
            val parts = trimmedDate.split(" ")
            val amount = parts[0].toLong()
            val now = ZonedDateTime.now(ZoneOffset.UTC)
            when (parts.getOrNull(1)) {
                "detik" -> now.minusSeconds(amount)
                "menit" -> now.minusMinutes(amount)
                "jam" -> now.minusHours(amount)
                "hari" -> now.minusDays(amount)
                "minggu" -> now.minusWeeks(amount)
                "bulan" -> now.minusMonths(amount)
                "tahun" -> now.minusYears(amount)
                else -> null
            }?.toInstant()?.toEpochMilli()
        }.getOrNull() ?: dateFormat.tryParseDate(dateString)
    }

    // =============================== Pages ================================
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        return document.select("div.reader-container img.page").mapIndexed { index, element ->
            Page(index, imageUrl = element.attr("abs:src"))
        }
    }

    // ============================== Filters ===============================
    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        SortFilter(),
        GenreFilter(),
        FormatFilter(),
        ProjectFilter(),
        StatusFilter(),
        AuthorFilter(),
        ArtistFilter(),
    )

    companion object {
        private val dateFormat = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("id"))
    }
}
