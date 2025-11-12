package eu.kanade.tachiyomi.extension.en.comix

import android.content.SharedPreferences
import android.util.Log
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.injectLazy

class Comix : HttpSource(), ConfigurableSource {

    override val name = "Comix"
    override val baseUrl = "https://comix.to/"
    private val apiUrl = "https://comix.to/api/v2/"
    override val lang = "en"
    override val supportsLatest = true

    private val preferences: SharedPreferences = getPreferences()

    private val json: Json by injectLazy()

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(5, 2)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun imageUrlParse(response: Response): String {
        throw UnsupportedOperationException()
    }

    /******************************* POPULAR MANGA ************************************/
    override fun popularMangaRequest(page: Int): Request {
        val url = apiUrl
            .toHttpUrl()
            .newBuilder()
            .addPathSegment("mangas")
            .addQueryParameter("order[views_30d]", "desc")
            .addQueryParameter("limit", "28")
            .addQueryParameter("page", page.toString())
            .build()

        Log.d("comix", url.toString())

        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val res: SearchResponse = response.parseAs()
        val manga = res.result.items.map { manga -> manga.toSManga(preferences.posterQuality()) }
        return MangasPage(manga, res.result.pagination.page < res.result.pagination.lastPage)
    }

    /******************************* LATEST MANGA ************************************/
    override fun latestUpdatesRequest(page: Int): Request {
        val url = apiUrl
            .toHttpUrl()
            .newBuilder()
            .addPathSegment("mangas")
            .addQueryParameter("order[chapter_updated_at]", "desc")
            .addQueryParameter("limit", "28")
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val res: SearchResponse = response.parseAs()
        val manga = res.result.items.map { manga -> manga.toSManga(preferences.posterQuality()) }
        return MangasPage(manga, res.result.pagination.page < res.result.pagination.lastPage)
    }

    /******************************* SEARCHING ***************************************/
    override fun searchMangaRequest(
        page: Int,
        query: String,
        filters: FilterList,
    ): Request {
        throw UnsupportedOperationException()
    }

    override fun searchMangaParse(response: Response): MangasPage {
        throw UnsupportedOperationException()
    }

    /******************************* Single Manga Page *******************************/
    override fun mangaDetailsRequest(manga: SManga): Request {
        val url = apiUrl
            .toHttpUrl()
            .newBuilder()
            .addPathSegment("mangas")
            .addPathSegment(manga.url)
            .build()

        return GET(url, headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val res: SingleMangaResponse = response.parseAs()

        return res.result.toSManga(preferences.posterQuality())
    }

    override fun getMangaUrl(manga: SManga): String {
        return "$baseUrl/title${manga.url}"
    }

    /******************************* Chapters List *******************************/
    override fun chapterListRequest(manga: SManga): Request {
        val url = apiUrl
            .toHttpUrl()
            .newBuilder()
            .addPathSegment("mangas")
            .addPathSegment(manga.url)
            .addPathSegment("chapters")
            .addQueryParameter("order[number]", "desc")
            .addQueryParameter("limit", "100")
            .build()

        return GET(url, headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        if (response.code == 204) {
            return emptyList()
        }

        val result: ChapterResponse = response.parseAs()

        val chapters = result.result.items.toMutableList()
        val requestUrl = response.request.url

        var hasNextPage = result.result.pagination.lastPage > result.result.pagination.page
        var page = result.result.pagination.page

        while (hasNextPage) {
            val url = requestUrl
                .newBuilder()
                .addQueryParameter("page", (++page).toString())
                .build()

            val newResponse = client.newCall(GET(url, headers)).execute()

            val newResult: ChapterResponse = newResponse.parseAs()

            chapters.addAll(newResult.result.items)

            hasNextPage = newResult.result.pagination.lastPage > newResult.result.pagination.page
        }

        return chapters.map { chapter -> chapter.toSChapter() }
    }

    /******************************* Page List (Reader) ************************************/
    override fun pageListRequest(chapter: SChapter): Request {
        return super.pageListRequest(chapter)
    }

    override fun pageListParse(response: Response): List<Page> {
        throw UnsupportedOperationException()
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val base = getMangaUrl()
        return "$baseUrl/title${manga.url}"
    }

    /******************************* PREFERENCES ************************************/
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = PREF_POSTER_QUALITY
            title = "Thumbnail Quality"
            summary = "Change the quality of the Thumbnail"
            entryValues = arrayOf("small", "medium", "large")
            entries = entryValues
            setDefaultValue("large")
        }.let(screen::addPreference)
    }
    private fun SharedPreferences.posterQuality() = getString(PREF_POSTER_QUALITY, "medium")
    companion object {
        private const val PREF_POSTER_QUALITY = "pref_poster_quality"
    }
}
