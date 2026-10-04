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
import keiyoushi.utils.asJsoup
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.tryParseDateTime
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.time.Instant

@Source
abstract class Manga168 : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        rateLimit(3) { it.host == baseUrl.toHttpUrl().host }
    }

    private val bangkokZone = ZoneId.of("Asia/Bangkok")
    private val dateTimeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    // The popular ranking is a fixed 15-item list, no pagination.
    override suspend fun getPopularManga(page: Int): MangasPage {
        if (page > 1) return MangasPage(emptyList(), false)
        val dto = client.get("$baseUrl/api/manga/daily-popular?period=weekly").parseAs<PopularDto>()
        return MangasPage(dto.data.map { it.toSManga() }, false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val dto = client.get("$baseUrl/api/manga/mangas?page=$page").parseAs<MangaListDto>()
        val hasNext = page < (dto.pagecount.toIntOrNull() ?: page)
        return MangasPage(dto.data.map { it.toSManga() }, hasNext)
    }

    // The API has no search endpoint, so search filters the bulk catalog
    // from the /manga page in memory. Results are paginated to keep the UI responsive.
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
            entries = entries.filter { entry -> genre in entry.genres }
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
            1 -> entries.sortedByDescending { it.viewsLong }
            else -> entries.sortedByDescending { it.updatedAt.toEpochMillis() }
        }

        val paged = entries.drop((page - 1) * SEARCH_PAGE_SIZE).take(SEARCH_PAGE_SIZE)
        return MangasPage(paged.map { it.toSManga() }, entries.size > page * SEARCH_PAGE_SIZE)
    }

    private suspend fun getCatalog(): List<SeriesDto> {
        val doc = client.get("$baseUrl/manga").asJsoup()
        return doc.extractNextJs<SeriesPageDto> { it is JsonObject && "series" in it }
            ?.series.orEmpty()
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val slug = url.pathSegments.getOrNull(1) ?: return null
        val manga = SManga.create().apply { this.url = "/manga/$slug" }
        return fetchMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = true)
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
        val doc = client.get("$baseUrl/manga/$slug").asJsoup()
        val series = doc.extractNextJs<SeriesPageDto> { it is JsonObject && "series" in it }
            ?.series?.firstOrNull { it.slug == slug }
            ?: return SMangaUpdate(manga, chapters)

        val updatedManga = SManga.create().apply {
            url = manga.url
            title = series.title.ifEmpty { manga.title }
            thumbnail_url = series.coverImage?.ifEmpty { null }
            author = series.author?.ifEmpty { null }
            description = series.description?.ifEmpty { null }
            genre = series.genres.filter { it.isNotBlank() }.joinToString().ifEmpty { null }
            status = when (series.status?.lowercase()) {
                "ongoing" -> SManga.ONGOING
                "completed" -> SManga.COMPLETED
                "hiatus" -> SManga.ON_HIATUS
                "dropped", "cancelled" -> SManga.CANCELLED
                else -> SManga.UNKNOWN
            }
        }

        val chapterList = series.chapters.mapNotNull { ch ->
            val number = ch.number ?: return@mapNotNull null
            if (ch.id.isEmpty()) return@mapNotNull null
            SChapter.create().apply {
                url = "/manga/$slug/chapter/${ch.id}"
                name = ch.title?.ifEmpty { null } ?: "Chapter ${number.toDisplayString()}"
                chapter_number = number.toFloat()
                date_upload = ch.updatedAt.toEpochMillis()
                memo = buildJsonObject {
                    put("mangaId", JsonPrimitive(series.id))
                    put("number", JsonPrimitive(number))
                }
            }
        }.sortedByDescending { it.chapter_number }

        return SMangaUpdate(updatedManga, chapterList)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val mangaId = chapter.memo["mangaId"]?.jsonPrimitive?.contentOrNull
            ?: error("Refresh chapter list")
        val number = chapter.memo["number"]?.jsonPrimitive?.doubleOrNull
            ?: error("Refresh chapter list")

        val imageUrls = client.get("$baseUrl/api/manga/mangas/$mangaId/${number.toDisplayString()}/images")
            .parseAs<ImagesDto>()
            .data
            .filter { it.isNotBlank() }

        return imageUrls.mapIndexed { index, url -> Page(index, imageUrl = url) }
    }

    private fun Double.toDisplayString(): String = if (this % 1.0 == 0.0) toInt().toString() else toString()

    private fun String?.toEpochMillis(): Long {
        if (this.isNullOrBlank()) return 0L
        return Instant.tryParse(this).takeIf { it != 0L }
            ?: dateTimeFormat.tryParseDateTime(this, bangkokZone)
    }

    private val SeriesDto.viewsLong: Long
        get() = views?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L

    override fun getFilterList(data: JsonElement?) = FilterList(
        SortFilter(),
        StatusFilter(),
        GenreFilter(),
    )

    companion object {
        private const val SEARCH_PAGE_SIZE = 20
    }
}
