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
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable

class Kuaikanmanhua : HttpSource() {

    override val name = "快看漫画"

    override val id: Long = 8099870292642776005

    override val baseUrl = "https://www.kuaikanmanhua.com"

    override val lang = "zh-Hans"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

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
        val newUrl = apiUrl + "/v1/topics/" + manga.url.trimEnd('/').substringAfterLast("/")
        val response = client.newCall(GET(newUrl, headers)).execute()
        val sManga = mangaDetailsParse(response).apply { initialized = true }
        return Observable.just(sManga)
    }

    override fun mangaDetailsParse(response: Response): SManga = SManga.create().apply {
        val data = response.parseAs<ApiMangaResponse>().data

        title = data.title
        thumbnail_url = data.verticalImageUrl
        author = data.user.nickname
        description = data.description
        status = data.updateStatusCode
    }

    // Chapters & Pages

    override fun chapterListRequest(manga: SManga): Request = GET(
        apiUrl + "/v1/topics/" + manga.url.trimEnd('/').substringAfterLast("/"),
        headers,
    )

    override fun chapterListParse(response: Response): List<SChapter> {
        val data = response.parseAs<ApiMangaResponse>().data

        return data.comics.map { comic ->
            SChapter.create().apply {
                url = "/web/comic/${comic.id}"
                name = comic.title + if (!comic.canView) " \uD83D\uDD12" else ""
                date_upload = comic.createdAt * 1000
            }
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
