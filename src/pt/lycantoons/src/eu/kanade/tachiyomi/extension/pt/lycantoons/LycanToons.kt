package eu.kanade.tachiyomi.extension.pt.lycantoons

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Source
abstract class LycanToons : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(2)
    }

    // The site refuses requests without Sec-Fetch-Site; its image CDN refuses any with Origin
    override fun Headers.Builder.configureHeaders(): Headers.Builder = removeAll("Origin")
        .set("Sec-Fetch-Site", "same-origin")

    override suspend fun getPopularManga(page: Int): MangasPage = metricsRequest("popular", page).parseAs<PopularResponse>().toMangasPage()

    override suspend fun getLatestUpdates(page: Int): MangasPage = metricsRequest("recently-updated", page).parseAs<PopularResponse>().toMangasPage()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        var search = query
        val tags = filters.selectedTags().toMutableList()

        val genreEntry = tagMapping.entries.find { it.value.equals(query, ignoreCase = true) }
        if (genreEntry != null) {
            tags.add(genreEntry.key)
            search = ""
        }

        val payload = SearchRequestBody(
            limit = PAGE_LIMIT,
            page = page,
            search = search,
            seriesType = filters.valueOrEmpty<SeriesTypeFilter>(),
            status = filters.valueOrEmpty<StatusFilter>(),
            tags = tags.distinct(),
        )

        return client.post("$baseUrl/api/series", payload.toJsonRequestBody())
            .parseAs<SearchResponse>()
            .toMangasPage()
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.getOrNull(0) != "series") return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf(String::isNotEmpty) ?: return null
        val manga = SManga.create().apply { this.url = "/series/$slug" }
        return fetchMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val slug = manga.slug()

        val details = async {
            if (fetchDetails) {
                client.get("$baseUrl/series/$slug").extractNextJs<SeriesDto>()!!.toSManga()
            } else {
                manga
            }
        }

        val chapterList = async {
            if (fetchChapters) {
                client.get("$baseUrl/series/$slug/1").extractNextJs<ChapterResponse>()!!.capitulos
                    .map { it.toSChapter(slug) }
                    .sortedByDescending { it.chapter_number }
            } else {
                chapters
            }
        }

        SMangaUpdate(details.await(), chapterList.await())
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterId = client.get(getChapterUrl(chapter)).extractNextJs<ChapterIdDto>()!!.capituloId

        return client.get("$baseUrl/api/chapters/$chapterId/view-pages")
            .parseAs<PageList>()
            .pages
            .mapIndexed { index, imageUrl -> Page(index, imageUrl = imageUrl) }
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        SeriesTypeFilter(),
        StatusFilter(),
        TagsFilter(),
    )

    private suspend fun metricsRequest(path: String, page: Int) = client.get("$baseUrl/api/metrics/$path?limit=$PAGE_LIMIT&page=$page")

    private fun SManga.slug(): String = url.substringBefore("?").substringAfterLast("/")

    companion object {
        private const val PAGE_LIMIT = 20
    }
}
