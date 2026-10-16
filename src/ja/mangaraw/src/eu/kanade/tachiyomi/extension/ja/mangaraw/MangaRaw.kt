package eu.kanade.tachiyomi.extension.ja.mangaraw

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
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Source
abstract class MangaRaw : KeiSource() {
    private val apiUrl = "https://api.mangarw.com/api/v1"

    override fun OkHttpClient.Builder.configureClient() = rateLimit(2)

    override suspend fun getPopularManga(page: Int): MangasPage = browse(page, "views_month")

    override suspend fun getLatestUpdates(page: Int): MangasPage = browse(page, "latest")

    private suspend fun browse(
        page: Int,
        sort: String,
    ): MangasPage {
        val url =
            "$apiUrl/browse/"
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("page", page.toString())
                .addQueryParameter("limit", "24")
                .addQueryParameter("sort", sort)
                .build()
        return client.get(url).parseAs<MangaListDto>().toMangasPage()
    }

    override suspend fun getSearchMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage {
        if (query.isBlank()) {
            return browse(page, filters.firstInstanceOrNull<SortFilter>()?.value ?: "views_month")
        }
        val url =
            "$apiUrl/search/"
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("keyword", query)
                .build()
        return client.get(url).parseAs<MangaListDto>().toMangasPage()
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        if (url.pathSegments.firstOrNull() != "manga") return null
        val id = url.pathSegments.getOrNull(1)?.toLongOrNull() ?: return null
        return client
            .get("$apiUrl/manga/$id")
            .parseAs<ApiResponse<MangaDto>>()
            .data
            .toSManga()
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/manga/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details =
            async {
                if (fetchDetails) {
                    client
                        .get("$apiUrl/manga/${manga.url}")
                        .parseAs<ApiResponse<MangaDto>>()
                        .data
                        .toSManga()
                } else {
                    manga
                }
            }
        val chapterList =
            async {
                if (fetchChapters) {
                    client
                        .get("$apiUrl/manga/${manga.url}/chapters?order=desc")
                        .parseAs<ApiResponse<List<ChapterDto>>>()
                        .data
                        .map { it.toSChapter() }
                } else {
                    chapters
                }
            }
        SMangaUpdate(details.await(), chapterList.await())
    }

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/chapter/${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> = client
        .get("$apiUrl/chapter/${chapter.url}")
        .parseAs<ApiResponse<ReaderDto>>()
        .data
        .toPages()

    override fun getFilterList(data: JsonElement?) = FilterList(SortFilter())

    private class SortFilter : Filter.Select<String>("並び順", arrayOf("月間人気", "週間人気", "日間人気", "最新更新")) {
        val value: String
            get() = arrayOf("views_month", "views_week", "views_day", "latest")[state]
    }
}
