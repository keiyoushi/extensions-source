package eu.kanade.tachiyomi.extension.th.manga168

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Source
abstract class Manga168 : KeiSource() {

    private val baseUrlHost by lazy { baseUrl.toHttpUrl().host }

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        rateLimit(3) { it.host == baseUrlHost }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val bangkokZone = ZoneId.of("Asia/Bangkok")
    private val dateTimeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    private var catalogCache: List<CatalogEntry>? = null

    override suspend fun getPopularManga(page: Int): MangasPage {
        val entries = getCatalog().sortedByDescending { it.views }
        return entries.toMangasPage(page)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val entries = getCatalog().sortedByDescending { it.updatedAt }
        return entries.toMangasPage(page)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        var entries = getCatalog()

        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            entries = entries.filter {
                it.title.lowercase().contains(q) || it.slug.lowercase().contains(q.replace(" ", "-"))
            }
        }

        val genre = filters.filterIsInstance<GenreFilter>().firstOrNull()?.selectedValue
        if (!genre.isNullOrEmpty()) {
            entries = entries.filter { entry -> entry.genres.any { it == genre } }
        }

        when (filters.filterIsInstance<StatusFilter>().firstOrNull()?.state) {
            1 -> entries = entries.filter { it.status.equals("ongoing", ignoreCase = true) }
            2 -> entries = entries.filter { it.status.equals("completed", ignoreCase = true) }
            3 -> entries = entries.filter {
                it.status.equals("hiatus", ignoreCase = true) ||
                    it.status.equals("dropped", ignoreCase = true)
            }
        }

        entries = when (filters.filterIsInstance<SortFilter>().firstOrNull()?.state) {
            1 -> entries.sortedByDescending { it.views }
            else -> entries.sortedByDescending { it.updatedAt }
        }

        return entries.toMangasPage(page)
    }

    private fun List<CatalogEntry>.toMangasPage(page: Int): MangasPage {
        val mangas = drop((page - 1) * PAGE_SIZE).take(PAGE_SIZE).map { it.toSManga() }
        return MangasPage(mangas, size > page * PAGE_SIZE)
    }

    private suspend fun getCatalog(): List<CatalogEntry> {
        catalogCache?.let { return it }

        val html = client.get("$baseUrl/manga").body.string()
        val entries = parseSeriesArray(html).mapNotNull { it.toCatalogEntry() }

        catalogCache = entries
        return entries
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val slug = url.pathSegments.getOrNull(1) ?: return null
        val manga = SManga.create().apply { this.url = "/manga/$slug" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false)
            .manga
            .apply { initialized = true }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val slug = manga.url.substringAfter("/manga/").substringBefore("/")
        val html = client.get("$baseUrl/manga/$slug").body.string()
        val series = parseSeriesArray(html).firstOrNull { it.slug() == slug }
            ?: return SMangaUpdate(manga, chapters)

        val updatedManga = SManga.create().apply {
            url = manga.url
            title = series.string("title").ifEmpty { manga.title }
            thumbnail_url = series.string("coverImage").ifEmpty { null }
            author = series.string("author").ifEmpty { null }
            description = series.string("description").ifEmpty { null }
            genre = series.jsonArray("genres").mapNotNull { it.jsonPrimitive.contentOrNull }
                .filter { it.isNotBlank() }
                .joinToString()
                .ifEmpty { null }
            status = when (series.string("status").lowercase()) {
                "ongoing" -> SManga.ONGOING
                "completed" -> SManga.COMPLETED
                "hiatus" -> SManga.ON_HIATUS
                "dropped", "cancelled" -> SManga.CANCELLED
                else -> SManga.UNKNOWN
            }
        }

        val chapterList = if (fetchChapters) {
            series.jsonArray("chapters").mapNotNull { element ->
                val obj = SeriesObject(element.jsonObject)
                val id = obj.string("id")
                val number = obj.double("number")
                if (id.isEmpty() || number == null) return@mapNotNull null
                SChapter.create().apply {
                    url = "/manga/$slug/chapter/$id"
                    name = obj.string("title").ifEmpty { "Chapter ${number.toDisplayString()}" }
                    chapter_number = number.toFloat()
                    date_upload = obj.string("updatedAt").toEpochMillis()
                }
            }.sortedByDescending { it.chapter_number }
        } else {
            chapters
        }

        return SMangaUpdate(updatedManga, chapterList)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val segments = chapter.url.split("/").filter { it.isNotEmpty() }
        val slug = segments.getOrNull(1) ?: error("Unexpected chapter URL: ${chapter.url}")
        val chapterId = segments.getOrNull(3) ?: error("Unexpected chapter URL: ${chapter.url}")

        val html = client.get("$baseUrl/manga/$slug").body.string()
        val series = parseSeriesArray(html).firstOrNull { it.slug() == slug }
            ?: error("Manga not found: $slug")
        val mangaId = series.string("id").ifEmpty { error("Manga id not found: $slug") }
        val number = series.jsonArray("chapters")
            .map { SeriesObject(it.jsonObject) }
            .firstOrNull { it.string("id") == chapterId }
            ?.double("number")
            ?: error("Chapter not found: $chapterId")

        val numberParam = number.toDisplayString()
        val response = client.get("$baseUrl/api/manga/mangas/$mangaId/$numberParam/images")
            .body.string()
        val imageUrls = json.parseToJsonElement(response).jsonObject["data"]?.jsonArray.orEmpty()
            .mapNotNull { it.jsonPrimitive.contentOrNull }
            .filter { it.isNotBlank() }

        return imageUrls.mapIndexed { index, url -> Page(index, imageUrl = url) }
    }

    private fun parseSeriesArray(html: String): List<SeriesObject> {
        val marker = "\\\"series\\\":["
        val start = html.indexOf(marker)
        if (start == -1) return emptyList()

        var i = start + marker.length
        var depth = 1
        var inString = false
        val raw = StringBuilder()
        while (i < html.length && depth > 0) {
            val c = html[i]
            if (c == '\\' && i + 1 < html.length) {
                val next = html[i + 1]
                if (next == '"') {
                    inString = !inString
                }
                raw.append(c).append(next)
                i += 2
                continue
            }
            if (!inString) {
                if (c == '[') {
                    depth++
                }
                if (c == ']') {
                    depth--
                }
            }
            if (depth > 0) raw.append(c)
            i++
        }

        val unescaped = json.decodeFromString<String>("\"$raw\"")
        return json.parseToJsonElement("[$unescaped]").jsonArray
            .map { SeriesObject(it.jsonObject) }
    }

    private fun Double.toDisplayString(): String = if (this % 1.0 == 0.0) toInt().toString() else toString()

    private fun String.toEpochMillis(): Long {
        if (isBlank()) return 0L
        runCatching {
            return Instant.parse(this).toEpochMilli()
        }
        runCatching {
            return LocalDateTime.parse(this, dateTimeFormat)
                .atZone(bangkokZone)
                .toInstant()
                .toEpochMilli()
        }
        return 0L
    }

    private class SeriesObject(private val obj: JsonObject) {
        fun string(key: String): String = obj[key]?.jsonPrimitive?.contentOrNull.orEmpty()
            .takeUnless { it == "\$undefined" }.orEmpty()

        fun double(key: String): Double? = obj[key]?.jsonPrimitive?.doubleOrNull

        fun slug(): String = string("slug")

        fun jsonArray(key: String) = obj[key]?.jsonArray.orEmpty()
    }

    private data class CatalogEntry(
        val slug: String,
        val title: String,
        val coverImage: String,
        val status: String,
        val genres: List<String>,
        val updatedAt: Long,
        val views: Long,
    ) {
        fun toSManga() = SManga.create().apply {
            url = "/manga/$slug"
            title = this@CatalogEntry.title
            thumbnail_url = coverImage.ifEmpty { null }
        }
    }

    private fun SeriesObject.toCatalogEntry(): CatalogEntry? {
        val slug = slug().ifEmpty { return null }
        val title = string("title").ifEmpty { return null }
        return CatalogEntry(
            slug = slug,
            title = title,
            coverImage = string("coverImage"),
            status = string("status"),
            genres = jsonArray("genres").mapNotNull { it.jsonPrimitive.contentOrNull },
            updatedAt = string("updatedAt").toEpochMillis(),
            views = string("views").toLongOrNull() ?: 0L,
        )
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        SortFilter(),
        StatusFilter(),
        GenreFilter(),
    )

    companion object {
        private const val PAGE_SIZE = 20
    }
}
