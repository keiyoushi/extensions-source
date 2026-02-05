package eu.kanade.tachiyomi.extension.all.qtoon

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.firstInstance
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable

class QToon(
    override val lang: String,
    private val siteLang: String,
) : HttpSource() {
    override val name = "QToon"

    private val domain = "qtoon.com"
    override val baseUrl = "https://$domain"
    private val apiUrl = "https://api.$domain"

    override val supportsLatest = true

    override val client = network.cloudflareClient

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int) = searchMangaRequest(page, "", getFilterList())

    override fun popularMangaParse(response: Response) = searchMangaParse(response)

    override fun latestUpdatesRequest(page: Int): Request {
        val url = apiUrl.toHttpUrl().newBuilder().apply {
            addPathSegments("api/w/ranking/page/comics")
            addQueryParameter("page", page.toString())
            addQueryParameter("rsid", "daily_hot")
        }.build()

        return apiRequest(url)
    }

    override fun latestUpdatesParse(response: Response) = searchMangaParse(response)

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith("https://")) {
            val urlPath = query.toHttpUrl().pathSegments
            val csid = if (
                urlPath.size == 2 &&
                (urlPath[0] == "detail" || urlPath[0] == "reader") &&
                siteLang == "en-US"
            ) {
                urlPath[1]
            } else if (
                urlPath.size == 3 &&
                (urlPath[1] == "detail" || urlPath[1] == "reader") &&
                urlPath[0] == siteLang.split("-", limit = 2)[0]
            ) {
                urlPath[2]
            } else {
                return Observable.just(MangasPage(emptyList(), false))
            }

            val url = apiUrl.toHttpUrl().newBuilder().apply {
                addPathSegments("api/w/comic/detail")
                addQueryParameter("csid", csid)
            }.build()

            return client.newCall(apiRequest(url))
                .asObservableSuccess()
                .map(::mangaDetailsParse)
                .map { MangasPage(listOf(it), false) }
        }

        return super.fetchSearchManga(page, query, filters)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val url = apiUrl.toHttpUrl().newBuilder().apply {
                addPathSegments("api/w/search/comic/search")
                addQueryParameter("title", query.trim())
                addQueryParameter("page", page.toString())
            }.build()

            return apiRequest(url)
        }

        val homePageSection = filters.firstInstance<HomePageFilter>().selected
        if (homePageSection.isNotBlank()) {
            val url = apiUrl.toHttpUrl().newBuilder().apply {
                addPathSegments("api/w/album/page/comics")
                addQueryParameter("page", page.toString())
                addQueryParameter("asid", homePageSection)
            }.build()

            return apiRequest(url)
        }

        val url = apiUrl.toHttpUrl().newBuilder().apply {
            addPathSegments("api/w/search/comic/gallery")
            addQueryParameter("area", "-1")
            addQueryParameter("tag", filters.firstInstance<TagFilter>().selected)
            addQueryParameter("gender", filters.firstInstance<GenderFilter>().selected)
            addQueryParameter("serialStatus", filters.firstInstance<StatusFilter>().selected)
            addQueryParameter("sortType", filters.firstInstance<SortFilter>().selected)
            addQueryParameter("page", page.toString())
        }.build()

        return apiRequest(url)
    }

    override fun getFilterList() = FilterList(
        Filter.Header("Filters don't work with text search"),
        TagFilter(),
        StatusFilter(),
        SortFilter(),
        GenderFilter(),
        Filter.Separator(),
        Filter.Header("Home Page section don't work with other filters"),
        HomePageFilter(),
    )

    override fun searchMangaParse(response: Response): MangasPage {
        val data = response.decryptAs<ComicsList>()

        return MangasPage(
            mangas = data.comics.map(Comic::toSManga),
            hasNextPage = data.more == 1,
        )
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        val comicUrl = manga.url.parseAs<ComicUrl>()

        val url = apiUrl.toHttpUrl().newBuilder().apply {
            addPathSegments("api/w/comic/detail")
            addQueryParameter("csid", comicUrl.webLinkId.ifBlank { comicUrl.csid })
        }.build()

        return apiRequest(url)
    }

    override fun getMangaUrl(manga: SManga): String {
        val comicUrl = manga.url.parseAs<ComicUrl>()
        val siteLangDir = siteLang.split("-", limit = 2).first()

        return buildString {
            append(baseUrl)
            if (siteLangDir != "en") {
                append("/")
                append(siteLangDir)
            }
            append("/detail/")
            append(comicUrl.webLinkId.ifBlank { comicUrl.csid })
        }
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val comic = response.decryptAs<ComicDetailsResponse>().comic

        return comic.toSManga()
    }

    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val episodes = response.decryptAs<ChapterEpisodes>().episodes
        val csid = response.request.url.queryParameter("csid")!!

        return episodes.map { episode ->
            SChapter.create().apply {
                url = EpisodeUrl(episode.esid, csid).toJsonString()
                name = episode.title
                chapter_number = episode.serialNo.toFloat()
            }
        }.asReversed()
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val episodeUrl = chapter.url.parseAs<EpisodeUrl>()

        val url = apiUrl.toHttpUrl().newBuilder().apply {
            addPathSegments("api/w/comic/episode/detail")
            addQueryParameter("esid", episodeUrl.esid)
        }.build()

        return apiRequest(url)
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val episodeUrl = chapter.url.parseAs<EpisodeUrl>()
        val siteLangDir = siteLang.split("-", limit = 2).first()

        return buildString {
            append(baseUrl)
            if (siteLangDir != "en") {
                append(("/"))
                append(siteLangDir)
            }
            append("/reader/")
            append(episodeUrl.csid)
            append("?chapter=")
            append(episodeUrl.esid)
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        val token = response.decryptAs<EpisodeResponse>().definitions[0].token

        val urlBuilder = apiUrl.toHttpUrl().newBuilder().apply {
            addPathSegments("api/w/resource/group/rslv")
            addQueryParameter("token", token)
        }
        var page = 1
        var hasNextPage = true
        val resources = mutableListOf<Resource>()

        while (hasNextPage) {
            val url = urlBuilder
                .setQueryParameter("page", page.toString())
                .build()

            val data = client.newCall(apiRequest(url)).execute()
                .decryptAs<EpisodeResources>()

            hasNextPage = data.more == 1
            resources.addAll(data.resources)
            page++
        }

        return resources.map {
            Page(it.rgIdx, imageUrl = decryptImageUrl(it.url, requestToken))
        }.sortedBy { it.index }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    private val requestToken = generateRandomString(24)

    private fun apiRequest(url: HttpUrl): Request {
        val headers = headersBuilder().apply {
            val platform = mobileUserAgentRegex.containsMatchIn(headers["User-Agent"]!!)
            add("platform", if (platform) "h5" else "pc")
            add("lth", siteLang)
            add("did", requestToken)
        }.build()

        return GET(url, headers)
    }
}
