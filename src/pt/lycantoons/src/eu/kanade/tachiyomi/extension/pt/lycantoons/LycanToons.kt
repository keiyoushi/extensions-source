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
import okhttp3.OkHttpClient

@Source
abstract class LycanToons : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(2)
    }

    // The site refuses requests without Sec-Fetch-Site; its image CDN refuses any with Origin
    override fun Headers.Builder.configureHeaders(): Headers.Builder = removeAll("Origin")
        .set("Sec-Fetch-Site", "same-origin")

    // =====================Popular=====================

    override suspend fun getPopularManga(page: Int): MangasPage = metricsRequest("popular", page).parseAs<PopularResponse>().toMangasPage()

    // =====================Latest=====================

    override suspend fun getLatestUpdates(page: Int): MangasPage = metricsRequest("recently-updated", page).parseAs<PopularResponse>().toMangasPage()

    // =====================Search=====================

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

    override fun getFilterList(data: JsonElement?): FilterList = LycanToonsFilters.get()

    // =====================Details=====================

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
                client.get("$baseUrl/series/$slug/1").extractNextJs<ChapterResponse>()?.capitulos!!
                    .map { it.toSChapter(slug) }
                    .sortedByDescending { it.chapter_number }
            } else {
                chapters
            }
        }

        SMangaUpdate(details.await(), chapterList.await())
    }

    // =====================Pages========================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterId = client.get("$baseUrl${chapter.url.substringBefore("?")}").extractNextJs<ChapterIdDto>()!!.capituloId

        return client.get("$baseUrl/api/chapters/$chapterId/view-pages")
            .parseAs<PageList>()
            .pages
            .mapIndexed { index, imageUrl -> Page(index, imageUrl = imageUrl) }
    }

    // =====================Utils=====================

    private suspend fun metricsRequest(path: String, page: Int) = client.get("$baseUrl/api/metrics/$path?limit=$PAGE_LIMIT&page=$page")

    private fun SManga.slug(): String = url.substringBefore("?").substringAfterLast("/")

    companion object {
        private const val PAGE_LIMIT = 20
    }
}
