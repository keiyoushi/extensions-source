package eu.kanade.tachiyomi.extension.fr.hentaizonebeta

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup

@Source
abstract class HentaiZoneBeta : KeiSource() {

    // ============================== Popular ===============================

    private var popularCursor: String? = null

    override suspend fun getPopularManga(page: Int): MangasPage {
        if (page == 1) popularCursor = null
        val result = getSearchMangaListInternal(popularCursor, "", FilterList(SortFilter("views")))
        popularCursor = result.nextCursor
        return MangasPage(result.mangas, result.hasNextPage)
    }

    // ============================== Latest ================================

    private var latestCursor: String? = null

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        if (page == 1) latestCursor = null
        val result = getSearchMangaListInternal(latestCursor, "", FilterList(SortFilter("latest")))
        latestCursor = result.nextCursor
        return MangasPage(result.mangas, result.hasNextPage)
    }

    // ============================== Search ================================

    private var searchCursor: String? = null

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (page == 1) searchCursor = null
        val result = getSearchMangaListInternal(searchCursor, query, filters)
        searchCursor = result.nextCursor
        return MangasPage(result.mangas, result.hasNextPage)
    }

    private class SearchResult(
        val mangas: List<SManga>,
        val nextCursor: String?,
        val hasNextPage: Boolean,
    )

    private suspend fun getSearchMangaListInternal(
        cursor: String?,
        query: String,
        filters: FilterList,
    ): SearchResult {
        val url = "$baseUrl/api/v1/manga/search".toHttpUrl().newBuilder().apply {
            addQueryParameter("limit", "24")

            if (!cursor.isNullOrBlank()) {
                addQueryParameter("cursor", cursor)
            }

            if (query.isNotBlank()) {
                addQueryParameter("search", query.trim())
            }

            var hasSort = false
            for (filter in filters) {
                when (filter) {
                    is SortFilter -> {
                        val sort = filter.toUriPart()
                        if (sort.isNotBlank()) {
                            addQueryParameter("orderby", sort)
                            hasSort = true
                        }
                    }
                    is TypeFilter -> {
                        val type = filter.toUriPart()
                        if (type.isNotBlank()) {
                            addQueryParameter("types", type)
                        }
                    }
                    is GenreList -> {
                        val selectedGenres = filter.state.filter { it.state }.map { it.value }
                        if (selectedGenres.isNotEmpty()) {
                            addQueryParameter("genres", selectedGenres.joinToString(","))
                        }
                    }
                    else -> {}
                }
            }

            if (!hasSort && query.isBlank()) {
                addQueryParameter("orderby", "latest")
                addQueryParameter("order", "desc")
            }
        }.build()

        val response = client.get(url).parseAs<SearchResponseDto>()
        val mangas = response.manga.map { it.toSManga() }
        val hasNextPage = response.hasNextPage && !response.nextCursor.isNullOrBlank()

        return SearchResult(mangas, response.nextCursor, hasNextPage)
    }

    private fun MangaDto.toSManga(): SManga = SManga.create().apply {
        url = permalink?.removeSuffix("/")?.substringAfterLast("/") ?: id.toString()
        title = this@toSManga.title
        thumbnail_url = thumbnail
        status = when (this@toSManga.status?.lowercase()) {
            "end", "completed", "terminé" -> SManga.COMPLETED
            "ongoing", "en cours" -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val segments = url.pathSegments.filter(String::isNotBlank)
        val slug = when {
            segments.size >= 2 && segments[0] == "manga" -> segments[1]
            segments.size == 1 -> segments[0]
            else -> return null
        }
        return fetchMangaUpdate(
            SManga.create().apply { this.url = slug },
            emptyList(),
            fetchDetails = true,
            fetchChapters = false,
        ).manga
    }

    // ============================== Filters ===============================

    override fun getFilterList(data: kotlinx.serialization.json.JsonElement?): FilterList = FilterList(
        SortFilter(),
        TypeFilter(),
        GenreList(getGenreList()),
    )

    // ============================== Details ===============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val apiUrl = "$baseUrl/api/v1/manga/single/${manga.url}"
        val mangaDetail = client.get(apiUrl).parseAs<MangaDetailDto>()

        val updatedManga = mangaDetail.toSManga()
        val chapterList = mangaDetail.chapters.map { it.toSChapter(manga.url) }

        return SMangaUpdate(updatedManga, chapterList)
    }

    private fun MangaDetailDto.toSManga(): SManga = SManga.create().apply {
        url = slug
        title = this@toSManga.title
        thumbnail_url = coverImage ?: bannerImage
        description = content?.takeIf(String::isNotBlank)?.let {
            Jsoup.parse(it).text()
        }
        author = authors.mapNotNull { it.name }.joinToString(", ")
        artist = artists.mapNotNull { it.name }.joinToString(", ")
        val allGenres = (genres.mapNotNull { it.name } + tags.mapNotNull { it.name }).distinct()
        genre = allGenres.joinToString(", ")
        status = when (this@toSManga.status?.lowercase()) {
            "end", "completed", "terminé" -> SManga.COMPLETED
            "ongoing", "en cours" -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }
    }

    private fun ChapterDto.toSChapter(mangaSlug: String): SChapter = SChapter.create().apply {
        url = "$mangaSlug/$chapterSlug"
        name = this@toSChapter.name
        date_upload = (uploadTime ?: date ?: 0L) * 1000L
        chapter_number = chapterNumber?.content?.toFloatOrNull()
            ?: Regex("""(?:chapitre|ch[.]?)\s*(\d+(?:[.]\d+)?)""", RegexOption.IGNORE_CASE)
                .find(name)?.groupValues?.get(1)?.toFloatOrNull()
            ?: -1f
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/manga/${chapter.url}"

    // ============================== Pages =================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val (mangaSlug, chapterSlug) = chapter.url.split('/')
        val apiUrl = "$baseUrl/api/v1/chapters/$mangaSlug/$chapterSlug"
        val response = client.get(apiUrl).parseAs<ChapterImagesDto>()

        return response.images.mapIndexed { index, img ->
            Page(index, imageUrl = img.url)
        }
    }
}
