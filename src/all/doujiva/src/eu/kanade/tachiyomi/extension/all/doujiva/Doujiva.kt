package eu.kanade.tachiyomi.extension.all.doujiva

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
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import kotlin.time.Instant

private const val PAGE_LIMIT = 24

@Source
abstract class Doujiva : KeiSource() {

    private val apiUrl get() = "$baseUrl/api/v1"

    // Stay well under the observed 100 req/min API budget.
    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(2) { it.host != "cdn.doujiva.com" }

    // ============================== Popular ==============================

    override suspend fun getPopularManga(page: Int): MangasPage = mangaList(page, sort = "popular-today")

    // ============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage = mangaList(page, sort = "newest")

    // ============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            return MangasPage(emptyList(), false)
        }

        val url = "$apiUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", trimmed)
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", PAGE_LIMIT.toString())
            .build()

        val response = client.get(url).parseAs<SearchResponse>()
        if (!response.ok || response.data.isEmpty()) {
            return MangasPage(emptyList(), false)
        }

        val hasNextPage = page < (response.meta?.totalPages ?: 1)
        return MangasPage(response.data.mapNotNull { it.toSMangaOrNull() }, hasNextPage)
    }

    // ============================== Details ==============================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val slug = slugFromUrl(url) ?: return null
        return fetchMangaDto(slug)?.toSMangaOrNull()?.apply { initialized = true }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val slug = manga.url

        val dto = fetchMangaDto(slug)
            ?: throw Exception("Doujiva manga not found: $slug")

        val details = dto.toSMangaOrNull()?.apply {
            url = manga.url
        } ?: manga

        val chapterList = dto.toSChapterList()

        return SMangaUpdate(details, chapterList)
    }

    // =============================== Pages ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterId = chapter.url
        val slug = chapter.memo["slug"]?.jsonPrimitive?.content
            ?: throw Exception("Missing Doujiva manga slug for chapter: ${chapter.url}")

        val response = client.get("$apiUrl/manga/$slug/chapters/$chapterId")
            .parseAs<ChapterPagesResponse>()

        return response.data.mapIndexed { index, page ->
            Page(index = index, imageUrl = page.imageUrl)
        }
    }

    // ============================== Helpers ==============================

    private suspend fun mangaList(page: Int, sort: String): MangasPage {
        val url = "$apiUrl/manga".toHttpUrl().newBuilder()
            .addQueryParameter("sort", sort)
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", PAGE_LIMIT.toString())
            .build()

        val response = client.get(url).parseAs<MangaListResponse>()
        if (!response.ok || response.data.isEmpty()) {
            return MangasPage(emptyList(), false)
        }

        val hasNextPage = page < (response.meta?.totalPages ?: 1)
        return MangasPage(response.data.mapNotNull { it.toSMangaOrNull() }, hasNextPage)
    }

    private suspend fun fetchMangaDto(slug: String): MangaDto? {
        val response = client.get("$apiUrl/manga/$slug").parseAs<MangaDetailResponse>()
        if (!response.ok) return null
        return response.data
    }

    private fun MangaDto.toSMangaOrNull(): SManga? {
        if (slug.isBlank() || title.isBlank()) return null
        return SManga.create().apply {
            url = slug
            memo = buildJsonObject {
                put("slug", slug)
            }
            title = this@toSMangaOrNull.title
            thumbnail_url = coverUrl?.takeIf { it.isNotBlank() }
            description = buildDescription()
            genre = tags
                .filter { it.category == "TAG" || it.category == "PARODY" }
                .map { it.name.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .joinToString()
                .ifBlank { null }
            // ARTIST and GROUP are the verified author-like fields on this API.
            author = tags
                .filter { it.category == "ARTIST" || it.category == "GROUP" }
                .map { it.name.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .joinToString()
                .ifBlank { null }
            status = when (this@toSMangaOrNull.status?.uppercase()) {
                "COMPLETED" -> SManga.COMPLETED
                "ONGOING" -> SManga.ONGOING
                "HIATUS", "ON_HIATUS" -> SManga.ON_HIATUS
                else -> SManga.UNKNOWN
            }
        }
    }

    private fun MangaDto.buildDescription(): String? {
        val parts = buildList {
            description?.takeIf { it.isNotBlank() }?.let { add(it) }
            if (pageCount > 0) add("Pages: $pageCount")
            language?.takeIf { it.isNotBlank() }?.let { add("Language: $it") }
            mediaType?.takeIf { it.isNotBlank() }?.let { add("Type: $it") }
            sourceName?.takeIf { it.isNotBlank() }?.let { add("Source: $it") }
        }
        return parts.joinToString("\n").ifBlank { null }
    }

    private fun MangaDto.toSChapterList(): List<SChapter> = chapters.map { chapter ->
        SChapter.create().apply {
            url = chapter.id
            memo = buildJsonObject {
                put("slug", slug)
                put("number", chapter.number)
            }
            name = buildString {
                append("Chapter ${chapter.number.toString().removeSuffix(".0")}")
                chapter.title?.takeIf { it.isNotBlank() }?.let { append(" - $it") }
            }
            chapter_number = chapter.number
            date_upload = chapter.createdAt?.let { createdAt ->
                runCatching {
                    Instant.parse(createdAt).toEpochMilliseconds()
                }.getOrNull()
            } ?: 0L
        }
    }

    private fun slugFromUrl(url: HttpUrl): String? {
        val segments = url.pathSegments.filter { it.isNotBlank() }
        if (segments.size >= 2 && segments[0] == "manga") {
            return segments[1]
        }
        return null
    }
}
