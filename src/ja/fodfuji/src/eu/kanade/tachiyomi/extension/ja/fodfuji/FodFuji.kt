package eu.kanade.tachiyomi.extension.ja.fodfuji

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.addCookie
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.array
import keiyoushi.utils.get
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.io.IOException

@Source
abstract class FodFuji :
    KeiSource(),
    ConfigurableSource {
    private val apiUrl get() = "$baseUrl/web/books"
    private val preferences by getPreferencesLazy()

    override fun getHomeUrl(): String = "$baseUrl/books"

    override fun Headers.Builder.configureHeaders() = set("Zk-Web-Version", "1.3.5")

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor(ImageInterceptor())
        addCookie("sfsc" to "0")
        addInterceptor {
            val request = it.request()
            val response = it.proceed(request)
            if (response.code == 403 && request.url.toString().startsWith(apiUrl)) {
                throw IOException("This service is only available in Japan.")
            }
            response
        }
    }

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$apiUrl/genreRanking".toHttpUrl().newBuilder()
            .addQueryParameter("category", "0")
            .addQueryParameter("sort_type", "2")
            .addQueryParameter("page", page.toString())
            .build()

        val result = client.get(url).parseAs<RankingResponse>()
        val mangas = result.rankingBooks.map { it.toSManga() }
        return MangasPage(mangas, false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$apiUrl/newArrival".toHttpUrl().newBuilder()
            .addQueryParameter("category", "0")
            .addQueryParameter("sort_type", "0")
            .addQueryParameter("page", page.toString())
            .build()

        val result = client.get(url).parseAs<LatestResponse>()
        val mangas = result.newArrivalBooks.map { it.toSManga() }
        return MangasPage(mangas, result.hasNextPage())
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$apiUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("keyword", query)
            .addQueryParameter("page", page.toString())
            .build()

        val result = client.get(url).parseAs<SearchResponse>()
        val mangas = result.searchBooks.map { it.toSManga() }
        return MangasPage(mangas, result.searchInfo.hasNextPage())
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/books/${manga.url.substringBefore("/")}/${manga.memo["episodeId"]!!.string}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val bookId = manga.url.substringBefore("/") // for old url compatibility
        val episodeId = manga.memo["episodeId"]?.string ?: throw Exception("Refresh Chapter List")
        val url = "$apiUrl/detail".toHttpUrl().newBuilder()
            .addQueryParameter("book_id", bookId)
            .addQueryParameter("episode_id", episodeId)
            .build()

        val result = client.get(url).parseAs<DetailsResponse>()
        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        val chapterList = result.bookSeries
            .filter { !hideLocked || !it.isLocked }
            .map { it.toSChapter() }
            .reversed()

        return SMangaUpdate(
            result.bookDetail.toSManga(),
            chapterList,
        )
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/viewer/${chapter.memo["bookId"]!!.string}/${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = "$apiUrl/licenceKey".toHttpUrl().newBuilder()
            .addQueryParameter("book_id", chapter.memo["bookId"]!!.string)
            .addQueryParameter("episode_id", chapter.url)
            .build()

        val result = client.get(url).parseAs<ViewerResponse>()
        if (result.bookData == null) {
            throw Exception("Log in via WebView and purchase this product to read.")
        }

        val keys = result.pagesData!!.keys
        val guardianUrl = "${result.guardianServer}/${result.bookData.s3Key}"
        if (!result.bookData.imagedReflow) {
            return keys.array.mapIndexed { i, key ->
                Page(i, imageUrl = buildPageUrl(guardianUrl, "${i + 1}.jpg", result.additionalQueryString, key.string))
            }
        }

        val bookJsonUrl = "$guardianUrl/book.json".toHttpUrl().newBuilder()
            .encodedQuery(result.additionalQueryString)
            .build()

        val profiles = client.get(bookJsonUrl).parseAs<ReflowBook>().reflowData.profiles
        val profile = profiles.find { it.id == "mincho_medium" } ?: profiles.first()
        val key = keys[profile.id]!!.string

        return List(profile.bookInfo.pageCount) {
            Page(it, imageUrl = buildPageUrl(guardianUrl, "${profile.id}/${it + 1}.jpg", result.additionalQueryString, key))
        }
    }

    private fun buildPageUrl(guardianUrl: String, path: String, signedParams: String?, key: String): String = "$guardianUrl/$path".toHttpUrl().newBuilder()
        .encodedQuery(signedParams)
        .fragment(key)
        .build()
        .toString()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_LOCKED_PREF_KEY
            title = "Hide Locked Chapters"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    companion object {
        private const val HIDE_LOCKED_PREF_KEY = "hide_locked"
    }
}
