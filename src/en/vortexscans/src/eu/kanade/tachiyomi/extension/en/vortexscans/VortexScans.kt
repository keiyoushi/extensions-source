package eu.kanade.tachiyomi.extension.en.vortexscans

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
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
import rx.Observable
import uy.kohesive.injekt.injectLazy

class VortexScans : HttpSource() {

    override val name = "Vortex Scans"

    override val baseUrl = "https://vortexscans.com"

    override val lang = "en"

    override val supportsLatest = false

    override val client = network.cloudflareClient

    private val json by injectLazy<Json>()

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int) = searchMangaRequest(page, "", getFilterList())
    override fun popularMangaParse(response: Response) = searchMangaParse(response)

    override fun latestUpdatesRequest(page: Int) =
        throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response) =
        throw UnsupportedOperationException()

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
            .map { it.toSManga() }

        val hasNextPage = data.totalCount > (page * perPage)

        return MangasPage(entries, hasNextPage)
    }

    override fun getFilterList() = FilterList(
        StatusFilter(),
        TypeFilter(),
        GenreFilter(),
    )

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return client.newCall(mangaDetailsRequest(manga))
            .asObservableSuccess()
            .map { mangaDetailsParse(it, manga) }
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        val id = manga.url.substringAfterLast("#")
        val url = "$baseUrl/api/chapters?postId=$id&skip=0&take=1000&order=desc&userid="

        return GET(url, headers)
    }

    override fun getMangaUrl(manga: SManga): String {
        val slug = manga.url.substringBeforeLast("#")

        return "$baseUrl/series/$slug"
    }

    private fun mangaDetailsParse(response: Response, manga: SManga): SManga {
        val data = response.parseAs<Post<Manga>>()

        assert(!data.post.isNovel) { "Novels are unsupported" }

        // genres are only returned in search call
        // and not when fetching details
        return data.post.toSManga().apply {
            if (manga.genre != null) {
                val newGenre = buildSet {
                    addAll(manga.genre!!.split(", "))
                    addAll(genre!!.split(", "))
                }
                genre = newGenre.joinToString()
            }
        }
    }

    override fun mangaDetailsParse(response: Response) =
        throw UnsupportedOperationException()

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

        return document.select("main > section > img").mapIndexed { idx, img ->
            Page(idx, imageUrl = img.absUrl("src"))
        }
    }

    override fun imageUrlParse(response: Response) =
        throw UnsupportedOperationException()

    private inline fun <reified T> Response.parseAs(): T =
        json.decodeFromString(body.string())
}

private const val perPage = 18
