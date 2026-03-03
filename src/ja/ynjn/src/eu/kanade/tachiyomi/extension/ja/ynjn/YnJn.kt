package eu.kanade.tachiyomi.extension.ja.ynjn

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.util.Calendar
import java.util.TimeZone

class YnJn :
    HttpSource(),
    ConfigurableSource {
    override val name = "Young Jump+"
    private val domain = "ynjn.jp"
    override val baseUrl = "https://$domain"
    override val lang = "ja"
    override val supportsLatest = true

    private val apiUrl = "https://webapi.$domain"
    private val preferences: SharedPreferences by getPreferencesLazy()
    private val jst = TimeZone.getTimeZone("Asia/Tokyo")
    private var cachedlatestId: Int? = null
    private var cacheExpiry: Long = 0L

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(ImageInterceptor())
        .build()

    override fun popularMangaRequest(page: Int): Request {
        val url = "$apiUrl/title/ranking".toHttpUrl().newBuilder()
            .addQueryParameter("id", "1742")
            .addQueryParameter("type", "LIST")
            .addQueryParameter("rankingType", "RANKING")
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.parseAs<RankingResponse>()
        val mangas = result.data.ranking.titles.map { it.title.toSManga() }
        return MangasPage(mangas, false)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val featureUrl = "$apiUrl/title/feature".toHttpUrl()
        val now = System.currentTimeMillis()
        if (cachedlatestId == null || now >= cacheExpiry) {
            val cal = Calendar.getInstance(jst)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.add(Calendar.DAY_OF_MONTH, 1)
            cacheExpiry = cal.timeInMillis

            val getId = featureUrl.newBuilder()
                .addQueryParameter("displayLocation", "TOP_PAGE_RENSAI")
                .build()
            val request = GET(getId, headers)
            val response = client.newCall(request).execute()
            cachedlatestId = response.parseAs<DataResponse>().data.info?.id
        }

        val url = featureUrl.newBuilder()
            .addQueryParameter("id", cachedlatestId.toString())
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val result = response.parseAs<DataResponse>()
        val mangas = result.data.titles.map { it.toSManga() }
        return MangasPage(mangas, result.data.hasNext == true)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val url = "$apiUrl/title/category/TEXT".toHttpUrl().newBuilder()
                .addQueryParameter("category", "TEXT")
                .addQueryParameter("page", page.toString())
                .addQueryParameter("sort", "POPULARITY")
                .addQueryParameter("text", query)
                .build()
            return GET(url, headers)
        }

        val filter = filters.firstInstance<CategoryFilter>()
        val url = "$apiUrl/title/category/${filter.type}".toHttpUrl().newBuilder()
            .addQueryParameter("category", filter.type)
            .addQueryParameter("page", page.toString())
            .addQueryParameter("sort", "POPULARITY")
            .addQueryParameter("id", filter.value)
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val result = response.parseAs<DataResponse>()
        val mangas = result.data.titles.map { it.toSManga() }
        val page = response.request.url.queryParameter("page")!!.toInt()
        val hasNextPage = (page * 12) < result.data.totalCount
        return MangasPage(mangas, hasNextPage)
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        val url = "$apiUrl/book/${manga.url}"
        return GET(url, headers)
    }

    override fun mangaDetailsParse(response: Response): SManga = response.parseAs<TitleDetails>().data.book.toSManga()

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/title/${manga.url}"

    override fun chapterListRequest(manga: SManga): Request {
        val url = "$apiUrl/title/${manga.url}/episode".toHttpUrl().newBuilder()
            .addQueryParameter("isGetAll", "true")
            .build()
        return GET(url, headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val titleId = response.request.url.pathSegments[1]
        val result = response.parseAs<ChapterDetails>()
        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, true)
        return result.data.episodes
            .filter { !hideLocked || it.readingCondition == "EPISODE_READ_CONDITION_FREE" }
            .map { it.toSChapter(titleId) }
            .reversed()
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val httpUrl = "$baseUrl/${chapter.url}".toHttpUrl()
        val titleId = httpUrl.fragment
        val episodeId = httpUrl.pathSegments.first()
        return "$baseUrl/viewer/$titleId/$episodeId"
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val httpUrl = "$baseUrl/${chapter.url}".toHttpUrl()
        val titleId = httpUrl.fragment
        val episodeId = httpUrl.pathSegments.first()
        val url = "$apiUrl/viewer".toHttpUrl().newBuilder()
            .addQueryParameter("titleId", titleId)
            .addQueryParameter("episodeId", episodeId)
            .build()
        return GET(url, headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = response.parseAs<ViewerDetails>()
        if (result.data.pages.isEmpty()) {
            throw Exception("Log in via WebView and purchase this chapter.")
        }

        return result.data.pages
            .mapNotNull { it.mangaPage }
            .map {
                Page(it.pageNumber, imageUrl = "${it.pageImageUrl}#scramble")
            }
    }

    override fun getFilterList() = FilterList(
        CategoryFilter(),
    )

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_LOCKED_PREF_KEY
            title = "Hide Paid Chapters"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    companion object {
        private const val HIDE_LOCKED_PREF_KEY = "hide_locked"
    }
}
