package eu.kanade.tachiyomi.extension.tr.faichisubs

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class FaichiSubs : KeiSource() {

    // The site is mostly anime: its few manga all fit on one page, and it has no list of new chapters for them
    override val supportsLatest = false

    override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(fetchManga().sortedByDescending { it.viewCount }.map { it.toSManga() }, false)

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = MangasPage(fetchManga().filter { it.matches(query.trim()) }.map { it.toSManga() }, false)

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host || url.pathSegments[0] !in listOf("detay", "oku")) return null
        val key = url.pathSegments.getOrNull(1)?.substringAfterLast("-") ?: return null
        // Anime pages have the same addresses
        val series = fetchManga().firstOrNull { it.key == key } ?: return null
        return getMangaUpdate(series.toSManga(), emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    // The series' details and chapters only come with the data of one of its chapters
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val key = manga.url.substringAfterLast("-")
        val series = fetchManga().first { it.key == key }
        val episode = series.episodes.firstOrNull() ?: return SMangaUpdate(series.toSManga(), emptyList())
        val details = fetchChapterData(key, episode.number).series
        return SMangaUpdate(details.toSManga(), details.toSChapters())
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val (pathKey, number) = chapter.url.removePrefix("/oku/").split("/")
        return fetchChapterData(pathKey.substringAfterLast("-"), number).mangaPages
            .sortedBy { it.pageOrder }
            .mapIndexed { index, page -> Page(index, imageUrl = page.imageUrl) }
    }

    private suspend fun fetchManga(): List<SeriesDto> = client.get("$baseUrl/api/series", Fetch.XHR).parseAs<List<SeriesDto>>().filter { it.isManga }

    private suspend fun fetchChapterData(key: String, number: String): ChapterDataDto {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("api/manga")
            .addPathSegment(key)
            .addPathSegment(number)
            .build()
        return client.get(url, Fetch.XHR).parseAs<ChapterDataDto>()
    }
}
