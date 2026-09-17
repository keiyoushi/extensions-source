package eu.kanade.tachiyomi.extension.zh.kuaikanmanhua

import app.cash.quickjs.QuickJs
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable

@Source
abstract class Kuaikanmanhua : HttpSource() {

    override val supportsLatest = true

    private val apiUrl = "https://api.kkmh.com"

    // Popular

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/tag/0?region=1&pays=0&state=0&sort=2&page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val nuxtDefinition = document.selectFirst("script:containsData(__NUXT__)")!!.data()

        val onLastPage = document
            .selectFirst("ul.pagination li:nth-last-child(2) a")?.attr("class")?.contains("active") ?: true

        return QuickJs.create().use { quickJs ->
            quickJs.evaluate("var window = {};")
            quickJs.evaluate(nuxtDefinition)
            val nuxtJson = quickJs.evaluate("JSON.stringify(window.__NUXT__)") as String
            val mangaData = nuxtJson.parseAs<WebSearchPayload>()
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

            MangasPage(mangas, !onLastPage)
        }
    }

    private fun parseApiSearch(response: Response): MangasPage {
        val searchResponse = response.body.string().parseAs<ApiSearchResponse>()

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

    // Latest

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/tag/0?region=1&pays=0&state=0&sort=3&page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // Search

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith("https://")) {
            val url = query.toHttpUrl()
            val id = when (url.host) {
                "m.kuaikanmanhua.com" -> url.pathSegments[1]
                "www.kuaikanmanhua.com" -> url.pathSegments[2]
                else -> throw Exception("Unsupported url")
            }
            return fetchSearchManga(page, "$TOPIC_ID_SEARCH_PREFIX$id", filters)
        }
        if (query.startsWith(TOPIC_ID_SEARCH_PREFIX)) {
            val newQuery = query.removePrefix(TOPIC_ID_SEARCH_PREFIX)
            return client.newCall(GET("$apiUrl/v1/topics/$newQuery"))
                .asObservableSuccess()
                .map { response ->
                    val details = mangaDetailsParse(response)
                    details.url = "/web/topic/$newQuery"
                    MangasPage(listOf(details), false)
                }
        }
        return super.fetchSearchManga(page, query, filters)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = if (query.isNotEmpty()) {
        GET("$apiUrl/v1/search/topic?q=$query&since=${(page - 1) * DEFAULT_PAGE_SIZE}&size=$DEFAULT_PAGE_SIZE", headers)
    } else {
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
        GET("$baseUrl/tag/$genre?region=$region&pays=$pays&state=$status&sort=$sort&page=$page", headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val domain = response.request.url.host
        return if (domain == "api.kkmh.com") {
            parseApiSearch(response)
        } else {
            popularMangaParse(response)
        }
    }

    // Details

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        // Convert the stored url to one that works with the api
        val newUrl = baseUrl + manga.url
        val response = client.newCall(GET(newUrl, headers)).execute()
        val sManga = mangaDetailsParse(response).apply { initialized = true }
        return Observable.just(sManga)
    }

    fun parseUpdateStatus(status: String): Int = when (status) {
        "连载中" -> SManga.ONGOING
        "已完结" -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val nuxtDefinition = document.selectFirst("script:containsData(__NUXT__)")!!.data()

        return QuickJs.create().use { quickJs ->
            quickJs.evaluate("var window = {};")
            quickJs.evaluate(nuxtDefinition)
            val nuxtJson = quickJs.evaluate("JSON.stringify(window.__NUXT__)") as String
            val payload = nuxtJson.parseAs<WebMangaPayload>()
            val mangaData = requireNotNull(payload.data.getOrNull(0)?.topicInfo) {
                "Source did not return manga details"
            }

            SManga.create().apply {
                title = mangaData.title
                thumbnail_url = mangaData.verticalImageUrl
                author = mangaData.user.nickname
                description = mangaData.description
                status = parseUpdateStatus(mangaData.updateStatus)
            }
        }
    }

    // Chapters & Pages

    override fun chapterListRequest(manga: SManga): Request = GET(
        baseUrl + manga.url,
        headers,
    )

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val nuxtDefinition = document.selectFirst("script:containsData(__NUXT__)")!!.data()

        return QuickJs.create().use { quickJs ->
            quickJs.evaluate("var window = {};")
            quickJs.evaluate(nuxtDefinition)
            val nuxtJson = quickJs.evaluate("JSON.stringify(window.__NUXT__)") as String
            val chapters = nuxtJson.parseAs<WebMangaPayload>()
                .data
                .getOrNull(0)
                ?.comicList
                .orEmpty()

            chapters.map { comic ->
                SChapter.create().apply {
                    url = "/web/comic/${comic.id}"
                    name = comic.title
                    date_upload = comic.createdAt
                }
            }.reversed()
        }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        // if (chapter.name.endsWith("🔒")) {
        //    throw Exception("[此章节为付费内容]")
        // }
        return GET(baseUrl + chapter.url.replace("/web/comic/", "/webs/comic-next/"), headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val nuxtDefinition = document.selectFirst("script:containsData(__NUXT__)")!!.data()

        return QuickJs.create().use { quickJs ->
            quickJs.evaluate("var window = {};")
            quickJs.evaluate(nuxtDefinition)
            val images = (quickJs.evaluate("JSON.stringify(window.__NUXT__)") as String)
                .parseAs<WebChapterPayload>()
                .data
                .getOrNull(0)
                ?.res
                ?.data
                ?.comicInfo
                ?.comicImages
                .orEmpty()

            images.mapIndexed { index, image ->
                Page(index, "", image.url)
            }
        }
    }

    // Filters

    override fun getFilterList() = FilterList(
        Filter.Header("注意：不影響按標題搜索"),
        GenreFilter(),
        RegionFilter(),
        PaysFilter(),
        StatusFilter(),
        SortFilter(),
    )

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    companion object {
        const val TOPIC_ID_SEARCH_PREFIX = "topic:"
        const val DEFAULT_PAGE_SIZE = 10
    }
}
