package eu.kanade.tachiyomi.extension.id.soulscans

import eu.kanade.tachiyomi.source.model.Filter
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
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Source
abstract class SoulScans : KeiSource() {

    // ============================== CONFIGURATION ==============================

    override val supportsLatest = true

    private val apiUrl: String get() = "https://img.soulscans.asia/api"

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(2)

    override fun Headers.Builder.configureHeaders(): Headers.Builder = add("Referer", "$baseUrl/")

    // ============================== POPULAR ==============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$apiUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("type", "COMIC")
            .addQueryParameter("limit", "50")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("sort", "popular")
            .addQueryParameter("order", "desc")
            .build()

        val response = client.get(url)
        val result = response.parseAs<SearchResultDto>()

        return MangasPage(
            mangas = result.items.map { it.toSManga() },
            hasNextPage = result.page < result.totalPages,
        )
    }

    // ============================== LATEST ==============================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$apiUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("type", "COMIC")
            .addQueryParameter("limit", "50")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("sort", "latest")
            .addQueryParameter("order", "desc")
            .build()

        val response = client.get(url)
        val result = response.parseAs<SearchResultDto>()

        return MangasPage(
            mangas = result.items.map { it.toSManga() },
            hasNextPage = result.page < result.totalPages,
        )
    }

    // ============================== SEARCH ==============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$apiUrl/search".toHttpUrl().newBuilder().apply {
            addQueryParameter("type", "COMIC")
            addQueryParameter("limit", "50")
            addQueryParameter("page", page.toString())

            if (query.isNotBlank()) {
                addQueryParameter("q", query)
            }

            filters.forEach { filter ->
                when (filter) {
                    is StatusFilter -> {
                        val value = filter.toUriPart()
                        if (value.isNotEmpty()) addQueryParameter("status", value)
                    }
                    is TypeFilter -> {
                        val value = filter.toUriPart()
                        if (value.isNotEmpty()) addQueryParameter("comic_type", value)
                    }
                    is ColorFilter -> {
                        val value = filter.toUriPart()
                        if (value.isNotEmpty()) addQueryParameter("color_format", value)
                    }
                    is ReadingFilter -> {
                        val value = filter.toUriPart()
                        if (value.isNotEmpty()) addQueryParameter("reading_format", value)
                    }
                    is SortFilter -> {
                        val sortValue = filter.toSortPart()
                        if (sortValue.isNotEmpty()) addQueryParameter("sort", sortValue)
                        val orderValue = filter.toOrderPart()
                        if (orderValue.isNotEmpty()) addQueryParameter("order", orderValue)
                    }
                    is ProjectOnlyFilter -> {
                        if (filter.state) addQueryParameter("project_only", "1")
                    }
                    is AuthorFilter -> {
                        if (filter.state.isNotEmpty()) addQueryParameter("author", filter.state)
                    }
                    is ArtistFilter -> {
                        if (filter.state.isNotEmpty()) addQueryParameter("artist", filter.state)
                    }
                    is PublisherFilter -> {
                        if (filter.state.isNotEmpty()) addQueryParameter("publisher", filter.state)
                    }
                    is GenreGroup -> {
                        val selected = filter.state.filter { it.state }.map { it.slug }
                        if (selected.isNotEmpty()) {
                            addQueryParameter("genre", selected.joinToString(","))
                        }
                    }
                    else -> {}
                }
            }
        }.build()

        val response = client.get(url)
        val result = response.parseAs<SearchResultDto>()

        return MangasPage(
            mangas = result.items.map { it.toSManga() },
            hasNextPage = result.page < result.totalPages,
        )
    }

    // ============================== DETAILS ==============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val slug = manga.getSlug()
        val response = client.get("$apiUrl/series/comic/$slug")
        val mangaDto = response.parseAs<MangaDto>()

        val updatedManga = mangaDto.toSManga()
        val updatedChapters = mangaDto.units
            .sortedByDescending { it.sortNumber.toDoubleOrNull() ?: 0.0 }
            .map { it.toSChapter(slug) }

        return SMangaUpdate(updatedManga, updatedChapters)
    }

    // ============================== PAGES ==============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val (mangaSlug, chapterSlug) = parseChapterUrl(chapter.url)
            ?: throw Exception("Invalid chapter URL format: ${chapter.url}")

        val response = client.get("$apiUrl/series/comic/$mangaSlug/chapter/$chapterSlug")
        val result = response.parseAs<ChapterPageResponseDto>()

        return result.chapter.pages.map { page ->
            val imageUrl = page.imageUrl.replace("http://", "https://")
            Page(page.pageNumber - 1, imageUrl = imageUrl)
        }
    }

    // ============================== URL COMPATIBILITY ==============================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val pathSegments = url.pathSegments
        if (pathSegments.size >= 2 && pathSegments[0] == "comic") {
            val slug = pathSegments[1]
            val response = client.get("$apiUrl/series/comic/$slug")
            return response.parseAs<MangaDto>().toSManga()
        }
        if (pathSegments.size >= 2 && pathSegments[0] == "manga") {
            val slug = pathSegments[1]
            val response = client.get("$apiUrl/series/comic/$slug")
            return response.parseAs<MangaDto>().toSManga()
        }
        return null
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url.replace("/manga/", "/comic/")}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl${chapter.url}"

    // ============================== FILTERS ==============================

    override fun getFilterList(data: kotlinx.serialization.json.JsonElement?): FilterList = FilterList(
        StatusFilter(),
        TypeFilter(),
        ColorFilter(),
        ReadingFilter(),
        Filter.Separator(),
        SortFilter(),
        ProjectOnlyFilter(),
        Filter.Separator(),
        AuthorFilter(),
        ArtistFilter(),
        PublisherFilter(),
        Filter.Separator(),
        GenreGroup(genreList),
    )

    // ============================== HELPERS & UTILITIES ==============================

    private fun SManga.getSlug(): String {
        val fullUrl = if (url.startsWith("http")) url else "$baseUrl/${url.removePrefix("/")}"
        return fullUrl.toHttpUrl().pathSegments.last { it.isNotBlank() }
    }

    private fun parseChapterUrl(chapterUrl: String): Pair<String, String>? {
        val parts = chapterUrl.removePrefix("/").removeSuffix("/").split("/")
        if (parts.size >= 4 && parts[parts.size - 4] == "comic" && parts[parts.size - 2] == "chapter") {
            return Pair(parts[parts.size - 3], parts[parts.size - 1])
        }
        if (parts.size >= 3 && parts[parts.size - 2] == "chapter") {
            return Pair(parts[parts.size - 3], parts[parts.size - 1])
        }
        val lastSegment = parts.last()
        val match = CHAPTER_URL_REGEX.matchEntire(lastSegment)
        if (match != null) {
            val mangaSlug = match.groupValues[1]
            val chapterNum = match.groupValues[2]
            val chapterSlug = "chapter-${chapterNum.removeSuffix(".0")}"
            return Pair(mangaSlug, chapterSlug)
        }
        return null
    }

    companion object {
        private val CHAPTER_URL_REGEX = """^(.*)-chapter-([\d.-]+)$""".toRegex()
    }
}
