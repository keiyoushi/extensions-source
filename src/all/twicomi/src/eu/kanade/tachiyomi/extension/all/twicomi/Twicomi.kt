package eu.kanade.tachiyomi.extension.all.twicomi

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.lang.IllegalArgumentException

@Source
abstract class Twicomi : KeiSource() {

    private val apiUrl = "https://api.twicomi.com/api/v2"

    override suspend fun getPopularManga(page: Int): MangasPage = getMangaList("$apiUrl/manga/featured/list?page_no=$page&page_limit=24".toHttpUrl())

    override suspend fun getLatestUpdates(page: Int): MangasPage = getMangaList("$apiUrl/manga/list?order_by=create_time&page_no=$page&page_limit=24".toHttpUrl())

    private suspend fun getMangaList(url: HttpUrl): MangasPage {
        val data = client.get(url).parseAs<TwicomiResponse<MangaListWithCount>>()
        val manga = data.response.mangaList.map { it.toSManga() }

        return MangasPage(manga, url.hasNextPage(data.response.totalCount))
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val searchAuthors = filters.firstInstanceOrNull<TypeSelect>()?.state == 1

        val url = apiUrl.toHttpUrl().newBuilder().apply {
            if (searchAuthors) {
                addPathSegment("author")
                filters.firstInstanceOrNull<AuthorSortFilter>()?.addToUrl(this)
            } else {
                addPathSegment("manga")
                filters.firstInstanceOrNull<MangaSortFilter>()?.addToUrl(this)
            }

            addPathSegment("list")

            if (query.isNotBlank()) {
                addQueryParameter("query", query)
            }

            addQueryParameter("page_no", page.toString())
            addQueryParameter("page_limit", "12")
        }.build()

        if (!searchAuthors) {
            return getMangaList(url)
        }

        val data = client.get(url).parseAs<TwicomiResponse<AuthorListWithCount>>()
        val manga = data.response.authorList.map { it.author.toSManga() }

        return MangasPage(manga, url.hasNextPage(data.response.totalCount))
    }

    private fun HttpUrl.hasNextPage(totalCount: Int): Boolean {
        val currentPage = queryParameter("page_no")!!.toInt()
        val pageLimit = queryParameter("page_limit")?.toInt() ?: 10
        return currentPage * pageLimit < totalCount
    }

    override fun getMangaUrl(manga: SManga): String = when (manga.url.split("/")[1]) {
        "author" -> baseUrl + manga.url + "/page/1"
        "manga" -> baseUrl + manga.url.substringBefore("#")
        else -> throw IllegalArgumentException()
    }

    override fun getChapterUrl(chapter: SChapter) = baseUrl + chapter.url.substringBefore("#")

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val splitUrl = manga.url.split("/")

        val chapterList = when (splitUrl[1]) {
            "manga" -> listOf(dummyChapterFromManga(manga))
            "author" -> if (fetchChapters) getAuthorChapterList(splitUrl[2]) else chapters
            else -> throw IllegalArgumentException()
        }

        return SMangaUpdate(manga, chapterList)
    }

    private suspend fun getAuthorChapterList(screenName: String): List<SChapter> {
        val pageLimit = 500
        val results = mutableListOf<MangaListItem>()
        var page = 0
        var totalCount: Int

        do {
            page += 1

            val url = "$apiUrl/author/manga/list?screen_name=$screenName&order_by=create_time&order=asc&page_no=$page&page_limit=$pageLimit"
            val data = client.get(url).parseAs<TwicomiResponse<MangaListWithCount>>()

            results.addAll(data.response.mangaList)
            totalCount = data.response.totalCount
        } while (page * pageLimit < totalCount)

        return results.mapIndexed { i, it ->
            dummyChapterFromManga(it.toSManga()).apply {
                name = it.tweet.tweetText.split("\n").first()
                chapter_number = i + 1F
            }
        }.reversed()
    }

    private fun dummyChapterFromManga(manga: SManga) = SChapter.create().apply {
        url = manga.url
        name = "Tweet"
        date_upload = manga.url.substringAfter("#").substringBefore(",").toLong()
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val urls = chapter.url.substringAfter("#").split(",").drop(1)
        return urls.mapIndexed { i, it -> Page(i, imageUrl = it) }
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        TypeSelect(),
        MangaSortFilter(),
        AuthorSortFilter(),
    )

    private class TypeSelect : Filter.Select<String>("Search for", arrayOf("Tweet", "Author"))

    data class Sortable(val title: String, val value: String) {
        override fun toString() = title
    }

    open class SortFilter(name: String, private val sortables: Array<Sortable>, state: Selection? = null) :
        Filter.Sort(
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

    class MangaSortFilter :
        SortFilter(
            "Sort (Tweet)",
            arrayOf(
                Sortable("Date", "create_time"),
                Sortable("Retweets", "retweet_count"),
                Sortable("Likes", "good_count"),
            ),
            Selection(0, false),
        )

    class AuthorSortFilter :
        SortFilter(
            "Sort (Author)",
            arrayOf(
                Sortable("Followers", "follower_count"),
                Sortable("Tweets", "manga_tweet_count"),
                Sortable("Recently tweeted", "latest_manga_tweet_time"),
            ),
            Selection(0, false),
        )
}
