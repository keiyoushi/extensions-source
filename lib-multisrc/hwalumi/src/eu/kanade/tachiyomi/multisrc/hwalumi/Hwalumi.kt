package eu.kanade.tachiyomi.multisrc.hwalumi

import android.util.Base64
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

abstract class Hwalumi : KeiSource() {

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
            addQueryParameter("sort", sortFilter)
            if (statusFilter.isNotEmpty()) addQueryParameter("status", statusFilter)
            if (typeFilter.isNotEmpty()) addQueryParameter("type", typeFilter)
            if (genreFilter.isNotEmpty()) addQueryParameter("genre", genreFilter)
            addQueryParameter("lang", "id")
            addQueryParameter("page", page.toString())
        }.build()

        val document = client.get(url).asJsoup()
        return parseMangaList(document, page)
    }

    private fun parseMangaList(document: Document, page: Int): MangasPage {
        val mangas = document.select("a[href^=\"/comic/\"]:has(img:not([src*=\"flagcdn\"]))").mapNotNull { element ->
            val img = element.selectFirst("img") ?: return@mapNotNull null
            val title = img.attr("alt").takeIf { it.isNotBlank() } ?: element.text().trim()
            if (title.isBlank()) return@mapNotNull null

            SManga.create().apply {
                this.title = title
                thumbnail_url = img.attr("abs:src")
                url = mangaSlug(element.attr("abs:href"))
            }
        }.distinctBy { it.url }

        val hasNextPage = document.select("a[href*=\"page=${page + 1}\"]").isNotEmpty()
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() !in listOf("comic", "komik")) return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null
        return fetchMangaUpdate(
            SManga.create().apply { this.url = slug },
            emptyList(),
            fetchDetails = true,
            fetchChapters = true,
        ).manga.apply { initialized = true }
    }

    private fun mangaSlug(url: String): String = (if (url.startsWith("http")) url else "$baseUrl/${url.trimStart('/')}").toHttpUrl().pathSegments.last { it.isNotEmpty() }

    private fun mangaUrl(url: String): String = "$baseUrl/comic/${mangaSlug(url)}"

    override fun getMangaUrl(manga: SManga): String = mangaUrl(manga.url)

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(mangaUrl(manga.url)).asJsoup()
        return SMangaUpdate(parseMangaDetails(manga, document), parseChapterList(document))
    }

    private fun parseMangaDetails(manga: SManga, document: Document): SManga = SManga.create().apply {
        url = manga.url
        title = document.selectFirst("h1")?.text()?.trim() ?: manga.title
        thumbnail_url = document.selectFirst("aside img[src*=\"/cover\"]")?.attr("abs:src") ?: manga.thumbnail_url
        description = run {
            val encoded = document.selectFirst("div[data-sr]")?.attr("data-sr")?.takeIf { it.isNotBlank() }
            encoded?.let(::decodeBase64) ?: document.selectFirst("p.text-sm")?.text()?.trim()?.takeIf { it.isNotBlank() }
        }
        author = document.selectFirst("div:has(> span:containsOwn(Author)) > span:last-child")?.text()?.takeUnless { it.equals("Updating", true) }
        artist = document.selectFirst("div:has(> span:containsOwn(Artist)) > span:last-child")?.text()?.takeUnless { it.equals("Updating", true) }
        genre = document.select("a[href*=\"genre=\"]")
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(", ")
        status = when (document.selectFirst("div:has(> div:containsOwn(Status)) > div:last-child")?.text()?.trim()?.lowercase()) {
            "completed", "tamat" -> SManga.COMPLETED
            "ongoing", "berjalan" -> SManga.ONGOING
            "hiatus" -> SManga.ON_HIATUS
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

    override suspend fun getPageList(chapter: SChapter): List<Page> = documentImages(client.get(baseUrl + chapter.url).asJsoup())

    private fun documentImages(document: Document): List<Page> = document.select("img[alt^=\"Page \"]")
        .map { it.attr("abs:src") }
        .filter { it.isNotBlank() && !it.contains("/api/image/p/") }
        .mapIndexed { index, imageUrl -> Page(index, imageUrl = imageUrl) }

    private fun decodeBase64(encoded: String): String? = try {
        val padded = encoded.trim().let { value ->
            val remainder = value.length % 4
            if (remainder == 0) value else value + "=".repeat(4 - remainder)
        }
        String(Base64.decode(padded, Base64.DEFAULT), Charsets.UTF_8).trim().takeIf { it.isNotBlank() }
    } catch (_: IllegalArgumentException) {
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
        val genres = data?.parseAs<Map<String, List<Map<String, String>>>>()?.get("genres")?.mapNotNull { map ->
            val name = map["name"]
            val value = map["value"]
            if (name != null && value != null) Genre(name, value) else null
        }

        return FilterList(
            SortFilter(),
            StatusFilter(),
            TypeFilter(),
            *listOfNotNull(genres?.takeIf { it.isNotEmpty() }?.let(::GenreListFilter)).toTypedArray(),
        )
    }
}
