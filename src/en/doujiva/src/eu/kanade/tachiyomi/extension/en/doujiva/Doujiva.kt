package eu.kanade.tachiyomi.extension.en.doujiva

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
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import kotlin.math.abs

@Source
abstract class Doujiva : KeiSource() {

    private val apiUrl = "$baseUrl/api/v1"

    // Stay well under the observed 100 req/min API budget.
    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(2)

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
        val slug = slugFromHttpUrl(url) ?: return null
        return fetchMangaDto(slug)?.toSMangaOrNull()?.apply { initialized = true }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val slug = slugFromMangaUrl(manga.url)
            ?: throw Exception("Cannot resolve Doujiva manga from url: ${manga.url}")

        val dto = fetchMangaDto(slug)
            ?: throw Exception("Doujiva manga not found: $slug")

        val details = if (fetchDetails) {
            dto.toSMangaOrNull()?.apply {
                initialized = true
                // Keep the relative URL already stored by the list/search entry.
                url = manga.url
            } ?: manga
        } else {
            manga
        }

        val chapterList = if (fetchChapters) {
            dto.toSChapterList()
        } else {
            chapters
        }

        return SMangaUpdate(details, chapterList)
    }

    // =============================== Pages ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val (slug, chapterNumber) = parseChapterUrl(chapter.url)
            ?: throw Exception("Invalid Doujiva chapter url: ${chapter.url}")

        val dto = fetchMangaDto(slug)
            ?: throw Exception("Doujiva manga not found: $slug")

        val match = findChapter(dto, chapterNumber)
            ?: throw Exception("Doujiva chapter not found for $slug #$chapterNumber")

        if (match.pages.isEmpty()) {
            throw Exception("Doujiva chapter has no pages: $slug #$chapterNumber")
        }

        return match.pages.mapIndexed { index, page ->
            val imageUrl = page.imageUrl
            if (imageUrl.isEmpty()) {
                throw Exception("Doujiva page ${index + 1} has no image URL")
            }
            Page(index = index, imageUrl = imageUrl)
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

    /**
     * Match a chapter by number with float tolerance, falling back to the sole chapter
     * only when the work has exactly one chapter (typical gallery case).
     */
    private fun findChapter(dto: MangaDto, chapterNumber: Float): ChapterDto? {
        val chapters = dto.chapters
        if (chapters.isEmpty()) return null

        chapters.firstOrNull { abs(it.number - chapterNumber) < CHAPTER_EPSILON }?.let { return it }

        // Only fall back when there is a single unambiguous chapter.
        if (chapters.size == 1) return chapters[0]

        return null
    }

    private fun MangaDto.toSMangaOrNull(): SManga? {
        if (slug.isBlank() || title.isBlank()) return null
        return SManga.create().apply {
            url = "/manga/$slug"
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

    private fun MangaDto.toSChapterList(): List<SChapter> {
        if (chapters.isEmpty()) {
            // Gallery with no chapter payload: synthesize a single chapter when pages exist.
            if (pageCount <= 0 && firstPageUrl.isNullOrBlank()) return emptyList()
            return listOf(
                SChapter.create().apply {
                    url = chapterUrl(slug, 1f)
                    name = "Chapter 1"
                    chapter_number = 1f
                    date_upload = uploadedAt.toEpochMillis()
                },
            )
        }

        return chapters.map { chapter ->
            SChapter.create().apply {
                url = chapterUrl(slug, chapter.number)
                name = buildString {
                    append("Chapter ${chapter.number.toChapterLabel()}")
                    chapter.title?.takeIf { it.isNotBlank() }?.let { append(" - $it") }
                }
                chapter_number = chapter.number
                date_upload = chapter.createdAt.toEpochMillis()
            }
        }
    }

    private fun chapterUrl(slug: String, number: Float): String = "/manga/$slug/chapter/${number.toChapterLabel()}"

    private fun slugFromMangaUrl(mangaUrl: String): String? {
        val path = mangaUrl.removePrefix(baseUrl).substringBefore('?').trim('/')
        val segments = path.split('/').filter { it.isNotEmpty() }
        if (segments.size >= 2 && segments[0] == "manga") {
            return segments[1]
        }
        return null
    }

    private fun slugFromHttpUrl(url: HttpUrl): String? {
        val segments = url.pathSegments.filter { it.isNotBlank() }
        if (segments.size >= 2 && segments[0] == "manga") {
            return segments[1]
        }
        return null
    }

    private fun parseChapterUrl(chapterUrl: String): Pair<String, Float>? {
        // Canonical form: /manga/{slug}/chapter/{number}
        val path = chapterUrl.removePrefix(baseUrl).substringBefore('?').trim('/')
        val segments = path.split('/').filter { it.isNotEmpty() }
        if (segments.size >= 4 && segments[0] == "manga" && segments[2] == "chapter") {
            val slug = segments[1]
            val number = segments[3].toFloatOrNull() ?: return null
            return slug to number
        }
        return null
    }

    private fun Float.toChapterLabel(): String = if (this % 1f == 0f) toInt().toString() else toString()

    private fun String?.toEpochMillis(): Long =
        this?.let { kotlin.time.Instant.parseOrNull(it)?.toEpochMilliseconds() } ?: 0L

    companion object {
        private const val PAGE_LIMIT = 24
        private const val CHAPTER_EPSILON = 0.001f
    }
}
