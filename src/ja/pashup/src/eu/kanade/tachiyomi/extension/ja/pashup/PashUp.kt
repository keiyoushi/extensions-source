package eu.kanade.tachiyomi.extension.ja.pashup

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
import keiyoushi.lib.publus.PublusContent
import keiyoushi.lib.publus.PublusInterceptor
import keiyoushi.lib.publus.fetchPages
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response

@Source
abstract class PashUp :
    KeiSource(),
    ConfigurableSource {
    private val apiUrl get() = "$baseUrl/pageapi"
    private val pageLimit = 10
    private val preferences by getPreferencesLazy()

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(PublusInterceptor())

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$apiUrl/contents.php".toHttpUrl().newBuilder()
            .addQueryParameter("type", "ranking")
            .addQueryParameter("period", "daily")
            .addQueryParameter("category", "2")
            .addQueryParameter("limit", pageLimit.toString())
            .addQueryParameter("offset", ((page - 1) * pageLimit).toString())
            .build()
        return client.get(url).toMangasPage(page)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$apiUrl/products.php".toHttpUrl().newBuilder()
            .addQueryParameter("type", "update")
            .addQueryParameter("period", "daily")
            .addQueryParameter("category", "2")
            .addQueryParameter("unit", "2")
            .addQueryParameter("lastest", "1")
            .addQueryParameter("limit", pageLimit.toString())
            .addQueryParameter("offset", ((page - 1) * pageLimit).toString())
            .build()
        return client.get(url).toMangasPage(page)
    }

    private fun Response.toMangasPage(page: Int): MangasPage {
        val result = this.parseAs<ContentResponse>()
        val mangas = result.contents.map { it.toSManga() }
        val hasNextPage = result.totalResults > page * pageLimit
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$apiUrl/contents.php".toHttpUrl().newBuilder()
            .addQueryParameter("type", "search")
            .addQueryParameter("reserve", "1")
            .addQueryParameter("keyword", query)
            .addQueryParameter("limit", "9999")
            .build()

        val mangas = client.get(url).parseAs<ContentResponse>().contents
            .filter { it.category == "2" }
            .map { it.toSManga() }
        return MangasPage(mangas, false)
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/content/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val url = "$apiUrl/products.php".toHttpUrl().newBuilder()
            .addQueryParameter("type", "contents")
            .addQueryParameter("id", manga.url)
            .addQueryParameter("limit", "9999")
            .addQueryParameter("order", "nodesc")
            .build()
        val contents = client.get(url).parseAs<ContentResponse>().contents
        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        val products = contents.mapNotNull { it.product }
            .filter { it.isAvailable && (!hideLocked || !it.isLocked) }
            .groupBy { it.salesUnit }

        val episodes = products["1"].orEmpty().map { it.toSChapter(manga.url) }.sortedByDescending { it.date_upload }
        val volumes = products["2"].orEmpty().map { it.toSChapter(manga.url) }.sortedByDescending { it.date_upload }

        return SMangaUpdate(
            contents.first().toSManga(),
            episodes + volumes,
        )
    }

    override fun getChapterUrl(chapter: SChapter): String = chapter.memo["viewerUrl"]?.string
        ?: "$baseUrl/content/${chapter.memo["seriesId"]!!.string}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val viewerUrl = chapter.memo["viewerUrl"]?.string?.toHttpUrl()
            ?: throw Exception("Log in via WebView and purchase this product to read.")

        val url = "$apiUrl/viewer/c.php".toHttpUrl().newBuilder()
            .addQueryParameter("cid", viewerUrl.queryParameter("cid"))
            .build()

        val contentUrl = client.get(url).parseAs<PublusContent>().url
            ?: throw Exception("Refresh Chapter List")

        return client.fetchPages(contentUrl)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_LOCKED_PREF_KEY
            title = "Hide Paid Chapters"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    companion object {
        private const val HIDE_LOCKED_PREF_KEY = "hide_locked"
    }
}
