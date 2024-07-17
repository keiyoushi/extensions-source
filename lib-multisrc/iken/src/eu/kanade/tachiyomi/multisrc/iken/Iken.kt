package eu.kanade.tachiyomi.multisrc.iken

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
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.injectLazy

abstract class Iken(
    override val name: String,
    override val lang: String,
    override val baseUrl: String,
) : HttpSource() {

    override val supportsLatest = true

    override val client = network.cloudflareClient

    private val json by injectLazy<Json>()

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    private var genres = emptyList<Pair<String, String>>()
    private val titleCache by lazy {
        val response = client.newCall(GET("$baseUrl/api/query?perPage=9999", headers)).execute()
        val data = response.parseAs<SearchResponse>()

        data.posts
            .filterNot { it.isNovel }
            .also { posts ->
                genres = posts.flatMap {
                    it.genres.map { genre ->
                        genre.name to genre.id.toString()
                    }
                }
            }
            .associateBy { it.slug }
    }

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/home", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val slugs = document.select("div:contains(Popular) + div.swiper div.manga-swipe > a")
            .map { it.absUrl("href").substringAfterLast("/series/") }

        val entries = slugs.mapNotNull {
            titleCache[it]?.toSManga(baseUrl)
        }

        return MangasPage(entries, false)
    }

    override fun latestUpdatesRequest(page: Int) = searchMangaRequest(page, "", getFilterList())
    override fun latestUpdatesParse(response: Response) = searchMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/api/query".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            addQueryParameter("perPage", perPage.toString())
            addQueryParameter("searchTerm", query.trim())
            filters.filterIsInstance<UrlPartFilter>().forEach {
                it.addUrlParameter(this)
            }
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val data = response.parseAs<SearchResponse>()
        val page = response.request.url.queryParameter("page")!!.toInt()

        val entries = data.posts
            .filterNot { it.isNovel }
            .map { it.toSManga(baseUrl) }

        val hasNextPage = data.totalCount > (page * perPage)

        return MangasPage(entries, hasNextPage)
    }

    override fun getFilterList() = FilterList(
        StatusFilter(),
        TypeFilter(),
        GenreFilter(genres),
        Filter.Header("Open popular mangas if genre filter is empty"),
    )

    override fun mangaDetailsRequest(manga: SManga): Request {
        val id = manga.url.substringAfterLast("#")
        val url = "$baseUrl/api/chapters?postId=$id&skip=0&take=1000&order=desc&userid="

        return GET(url, headers)
    }

    override fun getMangaUrl(manga: SManga): String {
        val slug = manga.url.substringBeforeLast("#")

        return "$baseUrl/series/$slug"
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val data = response.parseAs<Post<Manga>>()

        assert(!data.post.isNovel) { "Novels are unsupported" }

        // genres are only returned in search call
        // and not when fetching details
        return data.post.toSManga(baseUrl).apply {
            genre = titleCache[data.post.slug]?.getGenres()
        }
    }

    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val data = response.parseAs<Post<ChapterListResponse>>()

        assert(!data.post.isNovel) { "Novels are unsupported" }

        return data.post.chapters
            .filter { it.isPublic() }
            .map { it.toSChapter(data.post.slug) }
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        return document.select("main section > img").mapIndexed { idx, img ->
            Page(idx, imageUrl = img.absUrl("src"))
        }
    }

    override fun imageUrlParse(response: Response) =
        throw UnsupportedOperationException()

    private inline fun <reified T> Response.parseAs(): T =
        json.decodeFromString(body.string())
}

private const val perPage = 18
