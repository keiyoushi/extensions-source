package eu.kanade.tachiyomi.extension.ja.pixivcomic

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
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

    private val timeAndHash by lazy {
        getTimeAndHash()
    }

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(ShuffledImageInterceptor(key))
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
                thumbnail_url = manga.main_image_url
                url = "/api/app/works/v5/${manga.id}"

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

        val mangas = latest.data.official_works.map { manga ->
            SManga.create().apply {
                title = manga.name
                thumbnail_url = manga.image.main
                url = "/api/app/works/v5/${manga.id}"
            }
        }

        return MangasPage(mangas, latest.data.next_page_number != null)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        // for searching with tags, all tags started with #
        if (query.startsWith("#")) {
            val tag = query.removePrefix("#")
            val url = apiBuilder()
                .addPathSegment("tags")
                .addPathSegment(tag)
                .addPathSegments("works/v2")
                .addQueryParameter("page", page.toString())
                .build()

            return GET(url, headers)
        }

        filters.forEach { filter ->
            when (filter) {
                is Category ->
                    if (filter.state != 0) {
                        val url = apiBuilder()
                            .addPathSegment("categories")
                            .addPathSegment(filter.values[filter.state])
                            .addPathSegments("works")
                            .addQueryParameter("page", page.toString())
                            .build()

                        return GET(url, headers)
                    }
                is Tag ->
                    if (filter.state.isNotBlank()) {
                        val url = apiBuilder()
                            .addPathSegment("tags")
                            .addPathSegment(filter.state)
                            .addPathSegments("works/v2")
                            .addQueryParameter("page", page.toString())
                            .build()

                        return GET(url, headers)
                    }
                else -> {}
            }
        }

        val url = apiBuilder()
            .addPathSegments("works/search/v2")
            .addPathSegment(query)
            .addQueryParameter("page", page.toString())
            .build()

        return GET(url.toString(), headers)
    }

    override fun searchMangaParse(response: Response) = latestUpdatesParse(response)

    override fun getFilterList() = FilterList(CategoryHeader(), Category(), TagHeader(), Tag())

    private class CategoryHeader : Filter.Header(CATEGORY_HEADER_TEXT)

    private class Category : Filter.Select<String>("Category", categories)

    private class TagHeader : Filter.Header(TAG_HEADER_TEXT)

    private class Tag : Filter.Text("Tag")

    override fun getMangaUrl(manga: SManga): String {
        val mangaId = manga.url.substringAfterLast("/")

        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("works")
            .addPathSegment(mangaId)
            .build()

        return url.toString()
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val manga = json.decodeFromString<ApiResponse<Manga>>(response.body.string())
        val mangaInfo = manga.data.official_work

        return SManga.create().apply {
            description = Jsoup.parse(mangaInfo.description).wholeText()
            author = mangaInfo.author

            val categories = mangaInfo.categories?.map { it.name } ?: listOf()
            val tags = mangaInfo.tags?.map { "#${it.name}" } ?: listOf()

            val genreString = categories.plus(tags).joinToString(", ")
            genre = genreString
        }
    }

    override fun chapterListRequest(manga: SManga): Request {
        val mangaId = manga.url.substringAfterLast("/")

        val url = apiBuilder()
            .addPathSegment("works")
            .addPathSegment(mangaId)
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
                val chapterUrl = apiBuilder()
                    .addPathSegment("episodes")
                    .addPathSegment(episode.id.toString())
                    .addPathSegment("read_v4")
                    .build()

                name = episode.numbering_title.plus(": ${episode.sub_title}")
                url = chapterUrl.toString()
                date_upload = episode.read_start_at
                chapter_number = i.toFloat()
            }
        }
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val chapterId = chapter.url.substringBeforeLast("/").substringAfterLast("/")

        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("viewer/stories")
            .addPathSegment(chapterId)
            .build()

        return url.toString()
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val header = headers.newBuilder()
            .add("X-Client-Time", timeAndHash.first)
            .add("X-Client-Hash", timeAndHash.second)
            .build()

        return GET(chapter.url, header)
    }

    override fun pageListParse(response: Response): List<Page> {
        val shuffledPages = json.decodeFromString<ApiResponse<Pages>>(response.body.string())

        return shuffledPages.data.reading_episode.pages.mapIndexed { i, page ->
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
        private const val CATEGORY_HEADER_TEXT = "Can only filter 1 type (category or tag) at a time"
        private const val TAG_HEADER_TEXT = "If this filter by tag is used, keep category at \n\"No Category\""
        private val categories = arrayOf(
            "No Category",
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
