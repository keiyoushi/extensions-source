package eu.kanade.tachiyomi.extension.ja.cycomi

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.util.Calendar
import java.util.TimeZone
import kotlin.collections.asSequence

class CyComi :
    HttpSource(),
    ConfigurableSource {
    override val name = "CyComi"
    private val domain = "cycomi.com"
    override val baseUrl = "https://cycomi.com"
    override val lang = "ja"
    override val supportsLatest = true

    private val apiUrl = "https://web.$domain/api"
    private val jst = TimeZone.getTimeZone("Asia/Tokyo")
    private val preferences: SharedPreferences by getPreferencesLazy()

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(ImageInterceptor())
        .build()

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/ranking/title/1", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val mangas = response.parseAsNextData<RankingList>().rankingTitleList.map { it.toSManga() }
        return MangasPage(mangas, false)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val cal = Calendar.getInstance(jst)
        if (cal.get(Calendar.HOUR_OF_DAY) < 12) {
            cal.add(Calendar.DAY_OF_MONTH, -1)
        }

        val dayValue = cal.get(Calendar.DAY_OF_WEEK) - 1
        val url = "$apiUrl/title/serialization/list/$dayValue"
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val mangas = response.parseAs<MangaData>().data.titles.map { it.toSManga() }
        return MangasPage(mangas, false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotEmpty()) {
            val url = "$apiUrl/search/list/1".toHttpUrl().newBuilder()
                .addQueryParameter("word", query)
                .build()
            return GET(url, headers)
        }

        val filter = filters.firstInstance<CategoryFilter>()
        val url = "$apiUrl/title/serialization/list/${filter.value}"
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = latestUpdatesParse(response)

    override fun mangaDetailsRequest(manga: SManga): Request {
        val url = "$apiUrl/title/detail".toHttpUrl().newBuilder()
            .addQueryParameter("titleId", manga.url)
            .build()
        return GET(url, headers)
    }

    override fun mangaDetailsParse(response: Response): SManga = response.parseAs<MangaResponse>().data.toSManga()

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/title/${manga.url}"

    override fun chapterListRequest(manga: SManga): Request = paginatedChapterRequest(manga.url, null)

    private fun paginatedChapterRequest(titleId: String, cursor: Long?): Request {
        val url = "$apiUrl/chapter/paginatedList".toHttpUrl().newBuilder()
            .addQueryParameter("sort", "2")
            .addQueryParameter("limit", "100")
            .addQueryParameter("titleId", titleId)
            .apply {
                if (cursor != null) {
                    addQueryParameter("cursor", cursor.toString())
                }
            }
            .build()
        return GET(url, headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        val chapters = mutableListOf<SChapter>()
        val titleId = response.request.url.queryParameter("titleId")!!

        var listResponse = response.parseAs<ChapterListResponse>()

        while (true) {
            chapters += listResponse.data
                .asSequence()
                .filter { !hideLocked || !it.isLocked }
                .map { it.toSChapter() }
                .toList()

            val cursor = listResponse.nextCursor ?: break
            listResponse = client.newCall(paginatedChapterRequest(titleId, cursor)).execute().parseAs()
        }

        return chapters
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/viewer/chapter/${chapter.url}"

    override fun pageListRequest(chapter: SChapter): Request {
        val url = "$baseUrl/${chapter.url}".toHttpUrl()
        val titleId = url.fragment!!.toInt()
        val chapterId = url.pathSegments.first().toInt()
        val requestUrl = "$apiUrl/chapter/page/list"
        val body = ViewerRequestBody(titleId, chapterId).toJsonString().toRequestBody("application/json".toMediaType())
        return POST(requestUrl, headers, body)
    }

    override fun pageListParse(response: Response): List<Page> {
        val results = response.parseAs<ViewerResponse>()
        if (results.data.pages.isEmpty()) {
            throw Exception("Log in via WebView and rent or purchase this chapter.")
        }

        return results.data.pages.map {
            Page(it.pageNumber, imageUrl = "${it.image}#decrypt")
        }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_LOCKED_PREF_KEY
            title = "Hide Paid Chapters"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    private inline fun <reified T> Response.parseAsNextData(): T {
        val script = this.asJsoup().selectFirst("script#__NEXT_DATA__")!!.data()
        return script.parseAs<NextData<T>>().props.pageProps
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun getFilterList() = FilterList(
        CategoryFilter(),
    )

    private class CategoryFilter :
        SelectFilter(
            "Category",
            arrayOf(
                Pair("月曜日", "1"),
                Pair("火曜日", "2"),
                Pair("水曜日", "3"),
                Pair("木曜日", "4"),
                Pair("金曜日", "5"),
                Pair("土曜日", "6"),
                Pair("日曜日", "0"),
                Pair("他", "7"),
            ),
        )

    private open class SelectFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        val value: String
            get() = vals[state].second
    }

    companion object {
        private const val HIDE_LOCKED_PREF_KEY = "hide_locked"
    }
}
