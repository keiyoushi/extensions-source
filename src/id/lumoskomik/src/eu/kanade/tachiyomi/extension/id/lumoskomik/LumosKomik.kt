package eu.kanade.tachiyomi.extension.id.lumoskomik

import android.util.Base64
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

@Source
abstract class LumosKomik : KeiSource() {

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get("$baseUrl/all-series?sort=popular&lang=id&page=$page").asJsoup()
        return parseMangaList(document, page)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val document = client.get("$baseUrl/all-series?sort=latest&lang=id&page=$page").asJsoup()
        return parseMangaList(document, page)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val sortFilter = filters.firstInstanceOrNull<SortFilter>()?.getValue() ?: "latest"
        val statusFilter = filters.firstInstanceOrNull<StatusFilter>()?.getValue().orEmpty()
        val typeFilter = filters.firstInstanceOrNull<TypeFilter>()?.getValue().orEmpty()
        val genreFilter = filters.firstInstanceOrNull<GenreListFilter>()?.getIncluded().orEmpty()

        val url = "$baseUrl/browse".toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) addQueryParameter("q", query)
            if (sortFilter.isNotEmpty()) addQueryParameter("sort", sortFilter)
            if (statusFilter.isNotEmpty()) addQueryParameter("status", statusFilter)
            if (typeFilter.isNotEmpty()) addQueryParameter("type", typeFilter)
            if (genreFilter.isNotEmpty()) addQueryParameter("genre", genreFilter)
            addQueryParameter("page", page.toString())
        }.build()

        val document = client.get(url).asJsoup()
        return parseMangaList(document, page)
    }

    private fun parseMangaList(document: Document, page: Int): MangasPage {
        val mangas = document.select("a[href^=\"/comic/\"]:has(img:not([src*=\"flagcdn\"]))").mapNotNull { element ->
            val img = element.selectFirst("img") ?: return@mapNotNull null
            val coverUrl = img.attr("abs:src")
            val title = img.attr("alt").takeIf { it.isNotBlank() } ?: element.text().trim()
            if (title.isBlank()) return@mapNotNull null

            SManga.create().apply {
                this.title = title
                thumbnail_url = coverUrl
                setUrlWithoutDomain(element.attr("abs:href"))
            }
        }.distinctBy { it.url }

        val hasNextPage = document.select("a[href*=\"page=${page + 1}\"]").isNotEmpty()
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        if (!fetchDetails && !fetchChapters) return SMangaUpdate(manga, chapters)
        val document = client.get(baseUrl + manga.url).asJsoup()
        val updatedManga = if (fetchDetails) parseMangaDetails(manga, document) else manga
        val updatedChapters = if (fetchChapters) parseChapterList(document) else chapters
        return SMangaUpdate(updatedManga, updatedChapters)
    }

    private fun parseMangaDetails(manga: SManga, document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("h1")?.text()?.trim() ?: manga.title
        thumbnail_url = document.selectFirst("aside img[src*=\"/cover\"], div.aspect-\\[3\\/4\\] img, main img")?.attr("abs:src") ?: manga.thumbnail_url
        description = run {
            val encoded = document.selectFirst("div[data-sr]")?.attr("data-sr")?.takeIf { it.isNotBlank() }
            val decoded = encoded?.let { decodeBase64(it) }?.trim()?.takeIf { it.isNotBlank() }
            decoded ?: document.selectFirst("p.text-sm")?.text()?.trim()?.takeIf { it.isNotBlank() }
        }
        genre = document.select("a[href*=\"genre=\"]")
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(", ")

        val statusText = document.selectFirst("main")?.text() ?: document.text()
        status = when {
            statusText.contains("completed", ignoreCase = true) || statusText.contains("tamat", ignoreCase = true) -> SManga.COMPLETED
            statusText.contains("ongoing", ignoreCase = true) || statusText.contains("berjalan", ignoreCase = true) -> SManga.ONGOING
            statusText.contains("hiatus", ignoreCase = true) -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }

    private fun parseChapterList(document: Document): List<SChapter> = document.select("a[href*=\"/read/\"][data-chapter]").map { element ->
        SChapter.create().apply {
            setUrlWithoutDomain(element.attr("abs:href"))
            name = element.selectFirst("span.text-sm, span[class*=\"font-semibold\"]")?.text()?.trim() ?: element.text().trim()
            chapter_number = element.attr("data-chapter").trim().toFloatOrNull() ?: -1f
            val dateStr = element.selectFirst("span.tabular-nums, span[class*=\"tabular-nums\"]")?.text()?.trim()
            date_upload = parseRelativeDate(dateStr)
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(baseUrl + chapter.url).asJsoup()
        return document.select("img[src*=\"file/comic\"], img[src*=\"imgsvr\"]").mapIndexedNotNull { index, element ->
            val src = element.attr("abs:src")
            if (src.isBlank() || src.contains("/api/image/p/")) {
                null
            } else {
                Page(index, imageUrl = src)
            }
        }
    }

    private fun decodeBase64(encoded: String): String? = try {
        val padded = encoded.trim().let { s ->
            val rem = s.length % 4
            if (rem != 0) s + "=".repeat(4 - rem) else s
        }
        String(Base64.decode(padded, Base64.DEFAULT), Charsets.UTF_8).trim().takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }

    private fun parseRelativeDate(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return 0L
        val trimmed = dateStr.lowercase().trim()
        if (trimmed == "baru saja") return System.currentTimeMillis()

        val number = Regex("\\d+").find(trimmed)?.value?.toLongOrNull() ?: return 0L
        val nowInstant = Instant.now()
        val nowZoned = ZonedDateTime.now(ZoneId.systemDefault())

        return when {
            "detik" in trimmed -> nowInstant.minus(number, ChronoUnit.SECONDS).toEpochMilli()
            "menit" in trimmed -> nowInstant.minus(number, ChronoUnit.MINUTES).toEpochMilli()
            "jam" in trimmed -> nowInstant.minus(number, ChronoUnit.HOURS).toEpochMilli()
            "hari" in trimmed -> nowInstant.minus(number, ChronoUnit.DAYS).toEpochMilli()
            "minggu" in trimmed -> nowInstant.minus(number * 7, ChronoUnit.DAYS).toEpochMilli()
            "bulan" in trimmed -> nowZoned.minusMonths(number).toInstant().toEpochMilli()
            "tahun" in trimmed -> nowZoned.minusYears(number).toInstant().toEpochMilli()
            else -> 0L
        }
    }

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement {
        val document = client.get("$baseUrl/browse").asJsoup()
        val genres = document.select("label[data-bf-genre-name] input.bf-genre-cb[value]").mapNotNull { input ->
            val value = input.attr("value").trim()
            val name = input.parent()?.selectFirst("span.truncate")?.text()?.trim() ?: return@mapNotNull null
            if (value.isBlank() || name.isBlank()) null else mapOf("name" to name, "value" to value)
        }.distinctBy { it["value"] }
        if (genres.isEmpty()) throw Exception("Failed to fetch genres")
        return mapOf("genres" to genres).toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = try {
            data?.parseAs<Map<String, List<Map<String, String>>>>()?.get("genres")?.mapNotNull { map ->
                val name = map["name"]
                val value = map["value"]
                if (name != null && value != null) Genre(name, value) else null
            }
        } catch (_: Exception) {
            null
        }

        return FilterList(
            SortFilter(),
            StatusFilter(),
            TypeFilter(),
            *listOfNotNull(genres?.takeIf { it.isNotEmpty() }?.let { GenreListFilter(it) }).toTypedArray(),
        )
    }

    private class SortFilter :
        Filter.Select<String>(
            "Urutkan",
            arrayOf("Terbaru", "Populer", "Rating", "A - Z"),
        ) {
        fun getValue(): String = when (state) {
            0 -> "latest"
            1 -> "popular"
            2 -> "rating"
            3 -> "az"
            else -> "latest"
        }
    }

    private class StatusFilter :
        Filter.Select<String>(
            "Status",
            arrayOf("Semua", "Ongoing", "Completed", "Hiatus"),
        ) {
        fun getValue(): String = when (state) {
            1 -> "ongoing"
            2 -> "completed"
            3 -> "hiatus"
            else -> ""
        }
    }

    private class TypeFilter :
        Filter.Select<String>(
            "Tipe",
            arrayOf("Semua", "Manga", "Manhwa", "Manhua"),
        ) {
        fun getValue(): String = when (state) {
            1 -> "manga"
            2 -> "manhwa"
            3 -> "manhua"
            else -> ""
        }
    }

    private class Genre(val name: String, val id: String)
    private class GenreCheckBox(val genre: Genre) : Filter.CheckBox(genre.name)
    private class GenreListFilter(genres: List<Genre>) : Filter.Group<GenreCheckBox>("Genre", genres.map(::GenreCheckBox)) {
        fun getIncluded(): String = state.filter { it.state }.joinToString(",") { it.genre.id }
    }
}
