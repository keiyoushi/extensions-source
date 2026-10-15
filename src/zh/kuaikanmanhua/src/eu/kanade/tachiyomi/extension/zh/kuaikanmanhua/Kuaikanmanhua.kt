package eu.kanade.tachiyomi.extension.zh.kuaikanmanhua

import app.cash.quickjs.QuickJs
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
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import org.jsoup.nodes.Document

@Source
abstract class Kuaikanmanhua : KeiSource() {

    private val apiUrl = "https://api.kkmh.com"

    // Popular

    override suspend fun getPopularManga(page: Int): MangasPage = getWebMangaList("$baseUrl/tag/0?region=1&pays=0&state=0&sort=2&page=$page")

    private suspend fun getWebMangaList(listUrl: String): MangasPage {
        val document = client.get(listUrl).asJsoup()

        val onLastPage = document
            .selectFirst("ul.pagination li:nth-last-child(2) a")?.attr("class")?.contains("active") ?: true

        val mangaData = document.parseNuxt<WebSearchPayload>()
            .data
            .getOrNull(0)
            ?.dataList
            .orEmpty()

        val mangas = mangaData.map { mangaDatum ->
            SManga.create().apply {
                title = mangaDatum.title
                thumbnail_url = mangaDatum.verticalImageUrl
                url = "/web/topic/${mangaDatum.id}"
            }
        }

        return MangasPage(mangas, !onLastPage)
    }

    private inline fun <reified T> Document.parseNuxt(): T {
        val nuxtDefinition = selectFirst("script:containsData(__NUXT__)")!!.data()

        return QuickJs.create().use { quickJs ->
            quickJs.evaluate("var window = {};")
            quickJs.evaluate(nuxtDefinition)
            (quickJs.evaluate("JSON.stringify(window.__NUXT__)") as String).parseAs<T>()
        }
    }

    // Latest

    override suspend fun getLatestUpdates(page: Int): MangasPage = getWebMangaList("$baseUrl/tag/0?region=1&pays=0&state=0&sort=3&page=$page")

    // Search

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val id = when (url.host) {
            "m.kuaikanmanhua.com" -> url.pathSegments.getOrNull(1)
            "www.kuaikanmanhua.com" -> url.pathSegments.getOrNull(2)
            else -> null
        } ?: return null

        val manga = SManga.create().apply { this.url = "/web/topic/$id" }
        return fetchMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotEmpty()) {
            val searchResponse = client.get("$apiUrl/v1/search/topic?q=$query&since=${(page - 1) * DEFAULT_PAGE_SIZE}&size=$DEFAULT_PAGE_SIZE")
                .parseAs<ApiSearchResponse>()

            val data = searchResponse.data ?: return MangasPage(emptyList(), false)

            val mangaList = data.hit.orEmpty().map { result ->
                SManga.create().apply {
                    title = result.title
                    thumbnail_url = result.verticalImageUrl
                    url = "/web/topic/${result.id}"
                }
            }

            return MangasPage(mangaList, data.since >= 0)
        }

        var genre = "0"
        var region = "1"
        var pays = "0"
        var status = "0"
        var sort = "1"
        filters.forEach { filter ->
            when (filter) {
                is GenreFilter -> {
                    genre = filter.toUriPart()
                }
                is RegionFilter -> {
                    region = filter.toUriPart()
                }
                is PaysFilter -> {
                    pays = filter.toUriPart()
                }
                is StatusFilter -> {
                    status = filter.toUriPart()
                }
                is SortFilter -> {
                    sort = filter.toUriPart()
                }
                else -> {}
            }
        }
        return getWebMangaList("$baseUrl/tag/$genre?region=$region&pays=$pays&state=$status&sort=$sort&page=$page")
    }

    // Details & Chapters

    private fun parseUpdateStatus(status: String): Int = when (status) {
        "连载中" -> SManga.ONGOING
        "已完结" -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val payload = client.get(baseUrl + manga.url).asJsoup().parseNuxt<WebMangaPayload>()
        val data = requireNotNull(payload.data.getOrNull(0)) {
            "Source did not return manga details"
        }
        val mangaData = data.topicInfo

        manga.apply {
            title = mangaData.title
            thumbnail_url = mangaData.verticalImageUrl
            author = mangaData.user.nickname
            description = mangaData.description
            status = parseUpdateStatus(mangaData.updateStatus)
        }

        val chapterList = data.comicList.map { comic ->
            SChapter.create().apply {
                url = "/web/comic/${comic.id}"
                name = comic.title
                date_upload = comic.createdAt
            }
        }.reversed()

        return SMangaUpdate(manga, chapterList)
    }

    // Pages

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val images = client.get(baseUrl + chapter.url.replace("/web/comic/", "/webs/comic-next/"))
            .asJsoup()
            .parseNuxt<WebChapterPayload>()
            .data
            .getOrNull(0)
            ?.res
            ?.data
            ?.comicInfo
            ?.comicImages
            .orEmpty()

        return images.mapIndexed { index, image ->
            Page(index, "", image.imageUrl)
        }
    }

    // Filters

    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("注意：不影響按標題搜索"),
        GenreFilter(),
        RegionFilter(),
        PaysFilter(),
        StatusFilter(),
        SortFilter(),
    )

    companion object {
        const val DEFAULT_PAGE_SIZE = 10
    }
}
