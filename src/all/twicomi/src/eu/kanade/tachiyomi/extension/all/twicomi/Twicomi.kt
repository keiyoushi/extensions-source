package eu.kanade.tachiyomi.extension.all.twicomi

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
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.lang.IllegalArgumentException

class Twicomi : HttpSource() {

    override val name = "Twicomi"

    override val lang = "all"

    override val baseUrl = "https://twicomi.com"

    private val apiUrl = "https://api.twicomi.com/api/v2"

    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private val json: Json by injectLazy()

    override fun popularMangaRequest(page: Int) = GET("$apiUrl/manga/featured/list?page_no=$page&page_limit=24")

    override fun popularMangaParse(response: Response): MangasPage {
        val data = response.parseAs<TwicomiResponse<MangaListWithCount>>()
        val manga = data.response.mangaList.map { it.toSManga() }

        val currentPage = response.request.url.queryParameter("page_no")!!.toInt()
        val pageLimit = response.request.url.queryParameter("page_limit")?.toInt() ?: 10
        val hasNextPage = currentPage * pageLimit < data.response.totalCount

        return MangasPage(manga, hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int) = GET("$apiUrl/manga/list?order_by=create_time&page_no=$page&page_limit=24")

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = apiUrl.toHttpUrl().newBuilder().apply {
            when (filters.find { it is TypeSelect }?.state) {
                1 -> {
                    addPathSegment("author")
                    filters.filterIsInstance<AuthorSortFilter>().firstOrNull()?.addToUrl(this)
                }
                else -> {
                    addPathSegment("manga")
                    filters.filterIsInstance<MangaSortFilter>().firstOrNull()?.addToUrl(this)
                }
            }

            addPathSegment("list")

            if (query.isNotBlank()) {
                addQueryParameter("query", query)
            }

            addQueryParameter("page_no", page.toString())
            addQueryParameter("page_limit", "12")
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        return when (response.request.url.toString().removePrefix(apiUrl).split("/")[1]) {
            "author" -> {
                val data = response.parseAs<TwicomiResponse<AuthorListWithCount>>()
                val manga = data.response.authorList.map { it.author.toSManga() }

                val currentPage = response.request.url.queryParameter("page_no")!!.toInt()
                val pageLimit = response.request.url.queryParameter("page_limit")?.toInt() ?: 10
                val hasNextPage = currentPage * pageLimit < data.response.totalCount

                MangasPage(manga, hasNextPage)
            }
            "manga" -> popularMangaParse(response)
            else -> throw IllegalArgumentException()
        }
    }

    override fun getMangaUrl(manga: SManga): String {
        return when (manga.url.split("/")[1]) {
            "author" -> baseUrl + manga.url + "/page/1"
            "manga" -> baseUrl + manga.url.substringBefore("#")
            else -> throw IllegalArgumentException()
        }
    }

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = Observable.just(manga)

    override fun mangaDetailsRequest(manga: SManga) = throw UnsupportedOperationException()

    override fun mangaDetailsParse(response: Response) = throw UnsupportedOperationException()

    override fun getChapterUrl(chapter: SChapter) = baseUrl + chapter.url.substringBefore("#")

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return when (manga.url.split("/")[1]) {
            "manga" -> Observable.just(listOf(dummyChapterFromManga(manga)))
            "author" -> super.fetchChapterList(manga)
            else -> throw IllegalArgumentException()
        }
    }

    override fun chapterListRequest(manga: SManga): Request {
        val splitUrl = manga.url.split("/")
        val entryType = splitUrl[1]

        if (entryType == "manga") {
            throw Exception("Can only request chapter list for authors")
        }

        val screenName = splitUrl[2]
        return paginatedChapterListRequest(screenName, 1)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val data = response.parseAs<TwicomiResponse<MangaListWithCount>>()
        val results = data.response.mangaList.toMutableList()

        val screenName = response.request.url.queryParameter("screen_name")!!

        val pageLimit = response.request.url.queryParameter("page_limit")?.toInt() ?: 10
        var page = 1
        var hasNextPage = page * pageLimit < data.response.totalCount

        while (hasNextPage) {
            page += 1

            val newRequest = paginatedChapterListRequest(screenName, page)
            val newResponse = client.newCall(newRequest).execute()
            val newData = newResponse.parseAs<TwicomiResponse<MangaListWithCount>>()

            results.addAll(newData.response.mangaList)

            hasNextPage = page * pageLimit < data.response.totalCount
        }

        return results.mapIndexed { i, it ->
            dummyChapterFromManga(it.toSManga()).apply {
                name = it.tweet.tweetText.split("\n").first()
                chapter_number = i + 1F
            }
        }.reversed()
    }

    private fun paginatedChapterListRequest(screenName: String, page: Int) =
        GET("$apiUrl/author/manga/list?screen_name=$screenName&order_by=create_time&order=asc&page_no=$page&page_limit=500")

    private fun dummyChapterFromManga(manga: SManga) = SChapter.create().apply {
        url = manga.url
        name = "Tweet"
        date_upload = manga.url.substringAfter("#").substringBefore(",").toLong()
    }

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        val urls = chapter.url.substringAfter("#").split(",").drop(1)
        val pages = urls.mapIndexed { i, it -> Page(i, imageUrl = it) }

        return Observable.just(pages)
    }

    override fun pageListParse(response: Response) = throw UnsupportedOperationException()

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    override fun getFilterList() = FilterList(
        TypeSelect(),
        MangaSortFilter(),
        AuthorSortFilter(),
    )

    private class TypeSelect : Filter.Select<String>("Search for", arrayOf("Tweet", "Author"))

    data class Sortable(val title: String, val value: String) {
        override fun toString() = title
    }

    open class SortFilter(name: String, private val sortables: Array<Sortable>, state: Selection? = null) : Filter.Sort(
        name,
        sortables.map(Sortable::title).toTypedArray(),
        state,
    ) {
        fun addToUrl(url: HttpUrl.Builder) {
            if (state == null) {
                return
            }

            val query = sortables[state!!.index].value
            val order = if (state!!.ascending) "asc" else "desc"

            url.addQueryParameter("order_by", query)
            url.addQueryParameter("order", order)
        }
    }

    class MangaSortFilter : SortFilter(
        "Sort (Tweet)",
        arrayOf(
            Sortable("Date", "create_time"),
            Sortable("Retweets", "retweet_count"),
            Sortable("Likes", "good_count"),
        ),
        Selection(0, false),
    )

    class AuthorSortFilter : SortFilter(
        "Sort (Author)",
        arrayOf(
            Sortable("Followers", "follower_count"),
            Sortable("Tweets", "manga_tweet_count"),
            Sortable("Recently tweeted", "latest_manga_tweet_time"),
        ),
        Selection(0, false),
    )

    private inline fun <reified T> Response.parseAs() = json.decodeFromString<T>(body.string())
}
