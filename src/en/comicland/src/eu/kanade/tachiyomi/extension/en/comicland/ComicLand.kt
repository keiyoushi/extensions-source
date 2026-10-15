package eu.kanade.tachiyomi.extension.en.comicland

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
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class ComicLand : KeiSource() {

    private val apiUrl = "https://api.comicland.org/api"

    // ============================== Popular ==============================
    override suspend fun getPopularManga(page: Int): MangasPage {
        val offset = (page - 1) * 20
        return mangaListParse("$apiUrl/comics/popular?offset=$offset&limit=20".toHttpUrl())
    }

    private suspend fun mangaListParse(url: HttpUrl): MangasPage {
        val res = client.get(url).parseAs<ApiResponse<PageData>>()
        val data = res.data ?: return MangasPage(emptyList(), false)

        return MangasPage(data.comics.map { it.toSManga() }, data.hasNextPage)
    }

    // ============================== Latest ===============================
    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val offset = (page - 1) * 20
        return mangaListParse("$apiUrl/comics?offset=$offset&limit=20&status=ongoing".toHttpUrl())
    }

    // ============================== Search ===============================
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val offset = (page - 1) * 20

        if (query.isNotBlank()) {
            val url = "$apiUrl/comic/search".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("offset", offset.toString())
                .addQueryParameter("limit", "20")
                .build()

            return mangaListParse(url)
        }

        val categoryFilter = filters.firstInstanceOrNull<Filters>()
        val endpoint = categoryFilter?.selectedEndpoint ?: "/comics"
        val status = categoryFilter?.selectedStatus

        val url = "$apiUrl$endpoint".toHttpUrl().newBuilder()
            .addQueryParameter("offset", offset.toString())
            .addQueryParameter("limit", "20")

        if (status != null) {
            url.addQueryParameter("status", status)
        }

        return mangaListParse(url.build())
    }

    // ============================== Details ==============================
    override fun getMangaUrl(manga: SManga): String = "$baseUrl/comic/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val res = client.get("$apiUrl/comic/detail?slug=${manga.url}").parseAs<ApiResponse<ComicDetailDto>>()
        val data = res.data ?: throw Exception("Failed to parse manga details")

        return SMangaUpdate(
            manga = data.toSManga(),
            chapters = data.chapters?.map { it.toSChapter(data.slug) }?.reversed() ?: emptyList(),
        )
    }

    // =============================== Pages ===============================
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val slug = chapter.url.substringAfter("/comic/").substringBefore("/chapter/")
        val index = chapter.url.substringAfter("/chapter/")

        val res = client.get("$apiUrl/chapter/pages_by_index?slug=$slug&index=$index").parseAs<ApiResponse<PagesData>>()
        val pages = res.data?.pages ?: emptyList()

        return pages.mapIndexed { index, url ->
            Page(index, imageUrl = url)
        }
    }

    // ============================== Filters ==============================
    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("Text search ignores Category filter"),
        Filters(),
    )
}
