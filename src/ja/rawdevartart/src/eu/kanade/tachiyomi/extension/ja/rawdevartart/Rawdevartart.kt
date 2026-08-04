package eu.kanade.tachiyomi.extension.ja.rawdevartart

import eu.kanade.tachiyomi.extension.ja.rawdevartart.dto.ChapterResponseDto
import eu.kanade.tachiyomi.extension.ja.rawdevartart.dto.MANGA_API_PREFIX
import eu.kanade.tachiyomi.extension.ja.rawdevartart.dto.MangaListResponseDto
import eu.kanade.tachiyomi.extension.ja.rawdevartart.dto.MangaResponseDto
import eu.kanade.tachiyomi.extension.ja.rawdevartart.dto.chapterToString
import eu.kanade.tachiyomi.extension.ja.rawdevartart.dto.extractMangaId
import eu.kanade.tachiyomi.source.model.Filter
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
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class Rawdevartart : KeiSource() {
    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(
        page,
        "",
        FilterList(
            SortFilter(),
            GenreFilter(genres),
        ),
    )

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val response = client.get("$baseUrl/spa/latest-manga?page=$page")
        val data = response.parseAs<MangaListResponseDto>()

        return data.toMangasPage()
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/spa".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())

            if (query.isNotEmpty()) {
                addPathSegment("search")
                addQueryParameter("query", query)

                return@apply
            }

            (if (filters.isEmpty()) getFilterList() else filters).forEach { f ->
                when (f) {
                    is UriFilter -> f.addToUri(this)
                    is GenreFilter -> {
                        addPathSegment("genre")
                        addPathSegment(f.values[f.state].path)
                    }

                    else -> {}
                }
            }
        }.build()

        val response = client.get(url)
        val data = response.parseAs<MangaListResponseDto>()

        return data.toMangasPage()
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val response = client.get("$baseUrl/$MANGA_API_PREFIX/${manga.url}")
        val data = response.parseAs<MangaResponseDto>()

        return SMangaUpdate(data.toSManga(), data.toSChapterList())
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val response = client.get("$baseUrl/${chapter.url}")
        val data = response.parseAs<ChapterResponseDto>()

        return data.toPageList()
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/g/ne${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String {
        val mangaId = chapter.extractMangaId()
        val chapterNumber = chapterToString(chapter.chapter_number)

        return "$baseUrl/read/ne$mangaId/chapter-$chapterNumber"
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("Filters are ignored when using text search."),
        StatusFilter(),
        SortFilter(),
        GenreFilter(genres),
    )
}
