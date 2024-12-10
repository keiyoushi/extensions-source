package eu.kanade.tachiyomi.extension.ja.pixivcomic

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import uy.kohesive.injekt.injectLazy

class PixivComic : HttpSource() {
    override val lang: String = "ja"
    override val supportsLatest = true
    override val name = "Pixivコミック"
    override val baseUrl = "https://comic.pixiv.net"

    private val json: Json by injectLazy()

    // since there's no page option for popular manga, we use this as storage storing manga id
    private val alreadyLoadedPopularMangaIds = mutableSetOf<Int>()

    // used to determine if popular manga has next page or not
    private var popularMangaCountRequested = 0

    /**
     * the key can be any kind of string with minimum length of 1,
     * the same key must be passed in [imageRequest] and [ShuffledImageInterceptor]
     */
    private val key by lazy {
        randomString()
    }

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(ShuffledImageInterceptor(key))
        .addNetworkInterceptor(::tagInterceptor)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("X-Requested-With", "pixivcomic")

    override fun popularMangaRequest(page: Int): Request {
        if (page == 1) alreadyLoadedPopularMangaIds.clear()
        popularMangaCountRequested = POPULAR_MANGA_COUNT_PER_PAGE * page

        val url = apiBuilder()
            .addPathSegments("rankings/popularity")
            .addQueryParameter("label", "総合")
            .addQueryParameter("count", popularMangaCountRequested.toString())
            .build()

        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val popular = json.decodeFromString<ApiResponse<Popular>>(response.body.string())

        val mangas = popular.data.ranking.filterNot {
            alreadyLoadedPopularMangaIds.contains(it.id)
        }.map { manga ->
            SManga.create().apply {
                title = manga.title
                thumbnail_url = manga.mainImageUrl
                url = manga.id.toString()

                alreadyLoadedPopularMangaIds.add(manga.id)
            }
        }

        return MangasPage(mangas, popular.data.ranking.size == popularMangaCountRequested)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val url = apiBuilder()
            .addPathSegments("works/recent_updates")
            .addQueryParameter("page", page.toString())
            .build()

        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val latest = json.decodeFromString<ApiResponse<Latest>>(response.body.string())

        val mangas = latest.data.officialWorks.map { manga ->
            SManga.create().apply {
                title = manga.name
                thumbnail_url = manga.image.main
                url = manga.id.toString()
            }
        }

        return MangasPage(mangas, latest.data.nextPageNumber != null)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val apiBuilder = apiBuilder()

        when {
            // for searching with tags, all tags started with #
            query.startsWith("#") -> {
                val tag = query.removePrefix("#")
                apiBuilder
                    .addPathSegment("tags")
                    .addPathSegment(tag)
                    .addPathSegments("works/v2")
                    .addQueryParameter("page", page.toString())
            }
            query.isNotBlank() -> {
                apiBuilder
                    .addPathSegments("works/search/v2")
                    .addPathSegment(query)
                    .addQueryParameter("page", page.toString())
            }
            else -> {
                var tagIsBlank = true
                filters.forEach { filter ->
                    when (filter) {
                        is Tag -> {
                            if (filter.state.isNotBlank()) {
                                apiBuilder
                                    .addPathSegment("tags")
                                    .addPathSegment(filter.state.removePrefix("#"))
                                    .addPathSegments("works/v2")
                                    .addQueryParameter("page", page.toString())
                                    .build()

                                tagIsBlank = false
                            }
                        }
                        is Category -> {
                            if (tagIsBlank) {
                                apiBuilder
                                    .addPathSegment("categories")
                                    .addPathSegment(filter.values[filter.state])
                                    .addPathSegments("works")
                                    .addQueryParameter("page", page.toString())
                                    .build()
                            }
                        }
                        else -> {}
                    }
                }
            }
        }

        return GET(apiBuilder.build(), headers)
    }

    override fun searchMangaParse(response: Response) = latestUpdatesParse(response)

    override fun getFilterList() = FilterList(TagHeader(), Tag(), CategoryHeader(), Category())

    private class TagHeader : Filter.Header(TAG_HEADER_TEXT)

    private class Tag : Filter.Text("Tag")

    private class CategoryHeader : Filter.Header(CATEGORY_HEADER_TEXT)

    private class Category : Filter.Select<String>("Category", categories)

    override fun mangaDetailsRequest(manga: SManga): Request {
        val url = apiBuilder()
            .addPathSegments("works/v5")
            .addPathSegment(manga.url)
            .build()

        return GET(url, headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val manga = json.decodeFromString<ApiResponse<Manga>>(response.body.string())
        val mangaInfo = manga.data.officialWork

        return SManga.create().apply {
            description = Jsoup.parse(mangaInfo.description).wholeText()
            author = mangaInfo.author

            val categories = mangaInfo.categories?.map { it.name } ?: listOf()
            val tags = mangaInfo.tags?.map { "#${it.name}" } ?: listOf()

            val genreString = categories.plus(tags).joinToString(", ")
            genre = genreString
        }
    }

    override fun getMangaUrl(manga: SManga): String {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("works")
            .addPathSegment(manga.url)
            .build()

        return url.toString()
    }

    override fun chapterListRequest(manga: SManga): Request {
        val url = apiBuilder()
            .addPathSegment("works")
            .addPathSegment(manga.url)
            .addPathSegments("episodes/v2")
            .addQueryParameter("order", "desc")
            .build()

        return GET(url, headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapters = json.decodeFromString<ApiResponse<Chapters>>(response.body.string())

        return chapters.data.episodes.filter { episodeInfo ->
            episodeInfo.episode != null
        }.mapIndexed { i, episodeInfo ->
            SChapter.create().apply {
                val episode = episodeInfo.episode!!

                name = episode.numberingTitle.plus(": ${episode.subTitle}")
                url = episode.id.toString()
                date_upload = episode.readStartAt
                chapter_number = i.toFloat()
            }
        }
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("viewer/stories")
            .addPathSegment(chapter.url)
            .build()

        return url.toString()
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val doc = client.newCall(GET(getChapterUrl(chapter), headers)).execute().asJsoup()
        val salt = doc.selectFirst("script#__NEXT_DATA__")!!.data().let {
            json.decodeFromString<JsonElement>(it).jsonObject["props"]!!.jsonObject["pageProps"]!!
                .jsonObject["salt"]!!.jsonPrimitive.content
        }
        val url = apiBuilder()
            .addPathSegment("episodes")
            .addPathSegment(chapter.url)
            .addPathSegment("read_v4")
            .build()
        val timeAndHash = getTimeAndHash(salt)
        val header = headers.newBuilder()
            .add("X-Client-Time", timeAndHash.first)
            .add("X-Client-Hash", timeAndHash.second)
            .build()

        return GET(url, header)
    }

    override fun pageListParse(response: Response): List<Page> {
        val shuffledPages = json.decodeFromString<ApiResponse<Pages>>(response.body.string())

        return shuffledPages.data.readingEpisode.pages.mapIndexed { i, page ->
            Page(i, imageUrl = page.url)
        }
    }

    override fun imageUrlParse(response: Response): String {
        throw UnsupportedOperationException()
    }

    override fun imageRequest(page: Page): Request {
        val header = headers.newBuilder()
            .add("X-Cobalt-Thumber-Parameter-GridShuffle-Key", key)
            .build()

        return GET(page.imageUrl!!, header)
    }

    private fun apiBuilder(): HttpUrl.Builder {
        return baseUrl.toHttpUrl()
            .newBuilder()
            .addPathSegments("api/app")
    }

    companion object {
        private const val POPULAR_MANGA_COUNT_PER_PAGE = 30
        private const val TAG_HEADER_TEXT = "Can only filter 1 type (Category or Tag) at a time"
        private const val CATEGORY_HEADER_TEXT = "This filter by Category is ignored if Tag isn't at blank"
        private val categories = arrayOf(
            "恋愛",
            "動物",
            "グルメ",
            "ファンタジー",
            "ホラー・ミステリー",
            "アクション",
            "エッセイ",
            "ギャグ・コメディ",
            "日常",
            "ヒューマンドラマ",
            "スポーツ",
            "お仕事",
            "BL",
            "TL",
            "百合",
            "pixivコミック限定",
            "映像化",
            "コミカライズ",
            "タテヨミ",
            "読み切り",
            "その他",
        )
    }
}
