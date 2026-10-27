package eu.kanade.tachiyomi.extension.fr.scanhentaimenu

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

@Source
abstract class XManga : KeiSource() {

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(SortFilter("views")))

    // ============================== Latest ================================

    override suspend fun getLatestUpdates(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(SortFilter("latest")))

    // ============================== Search ================================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/api/mangas".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())

            if (query.isNotBlank()) {
                addQueryParameter("search", query.trim())
            }

            var hasSort = false
            for (filter in filters) {
                when (filter) {
                    is SortFilter -> {
                        val sort = filter.toUriPart()
                        if (sort.isNotBlank()) {
                            addQueryParameter("sort", sort)
                            hasSort = true
                        }
                    }
                    is FormatFilter -> {
                        val format = filter.toUriPart()
                        if (format.isNotBlank()) {
                            addQueryParameter("format", format)
                        }
                    }
                    is StatusFilter -> {
                        val status = filter.toUriPart()
                        if (status.isNotBlank()) {
                            addQueryParameter("status", status)
                        }
                    }
                    is GenreFilter -> {
                        val genre = filter.toUriPart()
                        if (genre.isNotBlank()) {
                            addQueryParameter("genre", genre)
                        }
                    }
                    is GenreTextFilter -> {
                        if (filter.state.isNotBlank()) {
                            addQueryParameter("genre", filter.state.trim())
                        }
                    }
                    is TagTextFilter -> {
                        if (filter.state.isNotBlank()) {
                            addQueryParameter("tag", filter.state.trim())
                        }
                    }
                    else -> {}
                }
            }

            if (!hasSort && query.isBlank()) {
                addQueryParameter("sort", "latest")
            }
        }.build()

        val response = client.get(url).parseAs<MangaListResponse>()
        val mangas = response.mangas.map { it.toSManga(baseUrl) }
        val hasNextPage = response.pagination?.let { it.current < it.totalPages } ?: false
        return MangasPage(mangas, hasNextPage)
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
        FormatFilter(),
        StatusFilter(),
        GenreFilter(),
        GenreTextFilter(),
        TagTextFilter(),
    )

    // ============================== Details ===============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val apiUrl = "$baseUrl/api/mangas/${manga.url}"
        val response = client.get(apiUrl).parseAs<MangaDetailResponse>()
        val mangaDetail = response.manga ?: throw Exception("Manga introuvable (${manga.url})")

        val updatedManga = mangaDetail.toSManga(baseUrl)
        val chapterList = mangaDetail.chapters.map { it.toSChapter() }

        return SMangaUpdate(updatedManga, chapterList)
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/read/${chapter.url}"

    // ============================== Pages =================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val apiUrl = "$baseUrl/api/mangas/read/${chapter.url}"
        val response = client.get(apiUrl).parseAs<ChapterResponse>()
        val chapterDetail = response.chapter ?: throw Exception("Chapitre introuvable (${chapter.url})")

        return chapterDetail.pages.mapIndexed { index, pageItem ->
            Page(index, imageUrl = getPageUrl(pageItem.filePath))
        }
    }
}
