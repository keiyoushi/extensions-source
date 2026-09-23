package eu.kanade.tachiyomi.extension.fr.banchanscan

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.stringOrNull
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class BanchanScan : KeiSource() {

    override val supportsFilterFetching = true

    override suspend fun getPopularManga(page: Int): MangasPage {
        val data = homeData()
        val viewCounts = data.dailyTopViews.associate { it.webtoonId to it.toIntOrZero() }
        val sorted = data.webtoons
            .filter { it.id in data.publishedWebtoonIds }
            .sortedWith(
                compareByDescending<WebtoonDto> { viewCounts[it.id] ?: 0 }
                    .thenByDescending { it.updatedAt },
            )
        return sorted.toMangasPage(page)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val offset = (page - 1) * PAGE_SIZE
        val chapters = client.get(
            "$baseUrl/api/data/chapters?select=$CHAPTER_FIELDS" +
                "&status=eq.published&order=published_at.desc.nullslast,created_at.desc" +
                "&limit=$PAGE_SIZE&offset=$offset",
        ).parseAs<List<ChapterDto>>()

        if (chapters.isEmpty()) return MangasPage(emptyList(), false)

        val webtoonIds = chapters.map { it.webtoonId }.distinct()
        val webtoonsById = client.get(
            "$baseUrl/api/data/webtoons?select=*&id=in.(${webtoonIds.joinToString(",")})",
        ).parseAs<List<WebtoonDto>>().associateBy { it.id }

        val mangas = webtoonIds.mapNotNull { webtoonsById[it]?.toSManga() }
        return MangasPage(mangas, chapters.size == PAGE_SIZE)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val data = homeData()
        val normalizedQuery = query.trim().foldAccents()
        val selectedGenres = filters.firstInstanceOrNull<GenreFilter>()
            ?.state
            ?.filter { it.state }
            ?.map { it.name }
            .orEmpty()

        val results = data.webtoons
            .filter { it.id in data.publishedWebtoonIds }
            .filter { it.matches(normalizedQuery, selectedGenres) }
            .sortedBy { it.title }

        return results.toMangasPage(page)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val segments = url.pathSegments
        if (segments.size < 2 || segments[0] != "webtoon") return null
        return findWebtoonBySlug(segments[1])?.toSManga()
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/webtoon/${manga.title.toSlug()}"

    override fun getChapterUrl(chapter: SChapter): String {
        val mangaSlug = chapter.memo["mangaSlug"]?.stringOrNull.orEmpty()
        val chapterNumber = chapter.chapter_number.toString().removeSuffix(".0")
        return "$baseUrl/webtoon/$mangaSlug/chapitre_$chapterNumber"
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val webtoonId = manga.url
        val slug = manga.title.toSlug()

        return coroutineScope {
            val detailsDeferred = if (fetchDetails) async { fetchWebtoonDetails(webtoonId) } else null
            val chaptersDeferred = if (fetchChapters) async { fetchChapterList(webtoonId, slug) } else null

            SMangaUpdate(
                detailsDeferred?.await()?.toSManga() ?: manga,
                chaptersDeferred?.await() ?: chapters,
            )
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterId = chapter.url
        return client.get(
            "$baseUrl/api/data/chapter_pages?select=image_url,sort_order" +
                "&chapter_id=eq.$chapterId&order=sort_order.asc",
        ).parseAs<List<ChapterPageDto>>().map { Page(it.sortOrder, imageUrl = it.imageUrl) }
    }

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/api/data/genres?select=name&order=name.asc&limit=500").parseAs()

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<List<GenreDto>>()?.map { it.name }.orEmpty()
        return if (genres.isEmpty()) FilterList() else FilterList(GenreFilter(genres))
    }

    private suspend fun homeData(): HomeDataDto = client.get("$baseUrl/api/home-data").parseAs()

    private suspend fun findWebtoonBySlug(slug: String): WebtoonDto? = homeData().webtoons.firstOrNull { it.title.toSlug() == slug }

    private suspend fun fetchWebtoonDetails(id: String): WebtoonDto = client.get("$baseUrl/api/data/webtoons?select=*&id=eq.$id&limit=1").parseAs<List<WebtoonDto>>().first()

    private suspend fun fetchChapterList(id: String, slug: String): List<SChapter> {
        val chapters = client.get(
            "$baseUrl/api/data/chapters?select=$CHAPTER_FIELDS&webtoon_id=eq.$id&status=eq.published",
        ).parseAs<List<ChapterDto>>()

        val showSeason = chapters.mapNotNull { it.seasonOrNull }.distinct().size > 1
        return chapters
            .sortedWith(compareByDescending<ChapterDto> { it.seasonNumber }.thenByDescending { it.chapterNumberValue })
            .map { it.toSChapter(slug, showSeason) }
    }

    private fun List<WebtoonDto>.toMangasPage(page: Int): MangasPage {
        val fromIndex = (page - 1) * PAGE_SIZE
        if (fromIndex >= size) return MangasPage(emptyList(), false)
        val toIndex = minOf(fromIndex + PAGE_SIZE, size)
        return MangasPage(subList(fromIndex, toIndex).map { it.toSManga() }, toIndex < size)
    }

    companion object {
        private const val PAGE_SIZE = 20
        private const val CHAPTER_FIELDS =
            "id,webtoon_id,chapter_number,season,chapter_title,published_at,created_at,uploader_name"
    }
}
