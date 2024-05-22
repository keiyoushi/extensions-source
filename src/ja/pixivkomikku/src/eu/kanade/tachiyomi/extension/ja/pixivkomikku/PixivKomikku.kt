package eu.kanade.tachiyomi.extension.ja.pixivkomikku

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import eu.kanade.tachiyomi.lib.dataimage.DataImageInterceptor
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
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import uy.kohesive.injekt.injectLazy

class PixivKomikku : HttpSource() {
    override val lang: String = "ja"
    override val supportsLatest = true
    override val name = "Pixiv Komikku"
    override val baseUrl = "https://comic.pixiv.net"

    private val json: Json by injectLazy()
    private val alreadyLoadedPopularMangaIds = mutableSetOf<Int>()
    private lateinit var key: String

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(DataImageInterceptor())
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("X-Requested-With", "pixivcomic")

    override fun popularMangaRequest(page: Int): Request {
        if (page == 1) alreadyLoadedPopularMangaIds.clear()

        return GET(
            "$baseUrl/api/app/rankings/popularity?label=総合&count=${
            POPULAR_MANGA_COUNT_PER_PAGE * page}",
            headers,
        )
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val popular = json.decodeFromString<Popular>(response.body.string())

        val mangas = popular.data.ranking.filterNot {
            alreadyLoadedPopularMangaIds.contains(it.id)
        }.map { manga ->
            SManga.create().apply {
                title = manga.title
                thumbnail_url = manga.main_image_url
                setUrlWithoutDomain("/api/app/works/v5/${manga.id}")

                alreadyLoadedPopularMangaIds.add(manga.id)
            }
        }
        return MangasPage(mangas, true)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/api/app/works/recent_updates?page=$page", headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val latest = json.decodeFromString<Latest>(response.body.string())

        val mangas = latest.data.official_works.map { manga ->
            SManga.create().apply {
                title = manga.name
                thumbnail_url = manga.image.main
                setUrlWithoutDomain("/api/app/works/v5/${manga.id}")
            }
        }
        return MangasPage(mangas, true)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.startsWith("#")) {
            val tag = query.removePrefix("#")
            return GET("$baseUrl/api/app/tags/$tag/works/v2?page=$page", headers)
        }

        filters.forEach { filter ->
            when (filter) {
                is Category ->
                    if (filter.state != 0) {
                        return GET(
                            "$baseUrl/api/app/categories/${
                            filter.values[filter.state]}/works?page=$page",
                            headers,
                        )
                    }
                is Tag ->
                    if (filter.state.isNotBlank()) {
                        return GET(
                            "$baseUrl/api/app/tags/${
                            filter.state}/works/v2?page=$page",
                            headers,
                        )
                    }
                else -> {}
            }
        }

        return GET("$baseUrl/api/app/works/search/v2/$query?page=$page", headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val search = json.decodeFromString<Search>(response.body.string())

        val mangas = search.data.official_works.map { manga ->
            SManga.create().apply {
                title = manga.name
                thumbnail_url = manga.image.main
                setUrlWithoutDomain("/api/app/works/v5/${manga.id}")
            }
        }

        return if (mangas.size >= 21) {
            MangasPage(mangas, true)
        } else {
            MangasPage(mangas, false)
        }
    }

    override fun getFilterList() = FilterList(CategoryHeader(), Category(), TagHeader(), Tag())

    private class CategoryHeader : Filter.Header(CATEGORY_HEADER_TEXT)

    private class Category : Filter.Select<String>("Category", categories)

    private class TagHeader : Filter.Header(TAG_HEADER_TEXT)

    private class Tag : Filter.Text("Tag")

    override fun getMangaUrl(manga: SManga): String {
        val mangaId = manga.url.substringAfterLast("/")

        return "$baseUrl/works/$mangaId"
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val manga = json.decodeFromString<Manga>(response.body.string())
        val mangaInfo = manga.data.official_work

        return SManga.create().apply {
            description = Jsoup.parse(mangaInfo.description).wholeText()
            author = mangaInfo.author

            val categories = mangaInfo.categories.map { it.name }
            val tags = mangaInfo.tags.map { "#${it.name}" }

            val genreString = categories.plus(tags).joinToString(", ")
            genre = genreString
        }
    }

    override fun chapterListRequest(manga: SManga): Request {
        val mangaId = manga.url.substringAfterLast("/")

        return GET("$baseUrl/api/app/works/$mangaId/episodes/v2?order=desc", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapters = json.decodeFromString<Chapters>(response.body.string())

        return chapters.data.episodes.mapIndexed { i, episodeInfo ->
            SChapter.create().apply {
                val episode = episodeInfo.episode
                if (episode == null) {
                    name = "※${episodeInfo.message}"
                    url = ""
                } else {
                    name = episode.numbering_title.plus(": ${episode.sub_title}")
                    url = "/viewer/stories/${episode.id}"
                    date_upload = episode.read_start_at
                }
                chapter_number = i.toFloat()
            }
        }
    }

    override fun fetchPageList(chapter: SChapter) =
        if (chapter.name.contains("※") && chapter.url.isEmpty()) {
            throw Error(chapter.name.substringAfter("※"))
        } else {
            super.fetchPageList(chapter)
        }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun pageListRequest(chapter: SChapter): Request {
        val chapterId = chapter.url.substringAfterLast("/")
        val timeAndHash = getTimeAndHash()

        val header = headers.newBuilder()
            .add("X-Client-Time", timeAndHash.first)
            .add("X-Client-Hash", timeAndHash.second)
            .build()

        return GET("$baseUrl/api/app/episodes/$chapterId/read_v4", header)
    }

    override fun pageListParse(response: Response): List<Page> {
        Log.d("pagelistparse", response.peekBody(Long.MAX_VALUE).string())
        val shuffledPages = json.decodeFromString<Pages>(response.body.string())

        return shuffledPages.data.reading_episode.pages.mapIndexed { i, page ->
            Log.d("pagelistparse", "${page.url} ${page.key}")
            if (i == 0) key = page.key

            Page(i, page.url)
        }
    }

    override fun imageUrlRequest(page: Page): Request {
        Log.d("imageUrlRequest", page.url)
        val header = headers.newBuilder()
            .add("X-Cobalt-Thumber-Parameter-GridShuffle-Key", key)
            .build()

        return GET(page.url, header)
    }

    override fun imageUrlParse(response: Response): String {
        Log.d("imageUrlParse", "start")

        val base64 = response.body.toBase64ImageString(key)
        return "https://127.0.0.1/?image=data:image/png;base64,$base64"
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
