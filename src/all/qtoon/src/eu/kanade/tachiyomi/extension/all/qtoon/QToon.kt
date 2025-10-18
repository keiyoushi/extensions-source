package eu.kanade.tachiyomi.extension.all.qtoon

import android.util.Log
import eu.kanade.tachiyomi.network.GET
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

    override fun popularMangaRequest(page: Int): Request {
        val url = apiUrl.toHttpUrl().newBuilder().apply {
            addPathSegments("api/w/album/page/comics")
            addQueryParameter("page", page.toString())
            addQueryParameter("asid", "as_l9zC15glGlkcS7yIamHQ")
        }.build()

        return apiRequest(url)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val data = response.decryptAs<Comics>()

        return MangasPage(
            mangas = data.comics.map { comic ->
                SManga.create().apply {
                    url = ComicUrl(comic.csid, comic.webLinkId.orEmpty()).toJsonString()
                    title = comic.title
                    thumbnail_url = comic.image.thumb.url
                }
            },
            hasNextPage = data.more == 1,
        )
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val url = apiUrl.toHttpUrl().newBuilder().apply {
            addPathSegments("api/w/ranking/page/comics")
            addQueryParameter("page", page.toString())
            addQueryParameter("rsid", "daily_hot")
        }.build()

        return apiRequest(url)
    }

    override fun latestUpdatesParse(response: Response) =
        popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val url = apiUrl.toHttpUrl().newBuilder().apply {
                addPathSegments("api/w/search/comic/search")
                addQueryParameter("title", query.trim())
                addQueryParameter("page", page.toString())
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
    )

    override fun searchMangaParse(response: Response) =
        popularMangaParse(response)

    override fun mangaDetailsRequest(manga: SManga): Request {
        Log.d(name, manga.url)
        val comicUrl = manga.url.parseAs<ComicUrl>()

        val url = apiUrl.toHttpUrl().newBuilder().apply {
            addPathSegments("api/w/comic/detail")
            addQueryParameter("csid", comicUrl.webLinkId.ifBlank { comicUrl.csid })
        }.build()

        return apiRequest(url)
    }

    override fun getMangaUrl(manga: SManga): String {
        val comicUrl = manga.url.parseAs<ComicUrl>()
        return buildString {
            append(baseUrl)
            append("/detail/")
            append(comicUrl.webLinkId.ifBlank { comicUrl.csid })
        }
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val comic = response.decryptAs<ComicDetailsResponse>().comic

        return SManga.create().apply {
            url = ComicUrl(comic.csid, comic.webLinkId.orEmpty()).toJsonString()
            title = comic.title
            thumbnail_url = comic.image.thumb.url
            author = comic.author
            description = buildString {
                append(comic.introduction)
                if (!comic.updateMemo.isNullOrBlank()) {
                    append("\n\nUpdates: ", comic.updateMemo)
                }
            }
            genre = buildSet {
                comic.tags.mapTo(this) { it.name }
                comic.corners.cornerTags.mapTo(this) { it.name }
            }.joinToString()
            status = when (comic.serialStatus.lowercase()) {
                "serializing" -> SManga.ONGOING
                "finish" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }
    }

    override fun chapterListRequest(manga: SManga) =
        mangaDetailsRequest(manga)

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
        Log.d(name, chapter.url)
        val episodeUrl = chapter.url.parseAs<EpisodeUrl>()

        val url = apiUrl.toHttpUrl().newBuilder().apply {
            addPathSegments("api/w/comic/episode/detail")
            addQueryParameter("esid", episodeUrl.esid)
        }.build()

        return apiRequest(url)
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val episodeUrl = chapter.url.parseAs<EpisodeUrl>()

        return buildString {
            append(baseUrl)
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

    override fun imageUrlParse(response: Response): String {
        throw UnsupportedOperationException()
    }

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
