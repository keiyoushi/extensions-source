package eu.kanade.tachiyomi.extension.ja.corocoroonline

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
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAsProto
import keiyoushi.utils.toRequestBodyProto
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.util.Calendar
import java.util.TimeZone

@Source
abstract class CorocoroOnline :
    HttpSource(),
    ConfigurableSource {
    override val supportsLatest = true

    private val apiUrl = "$baseUrl/api/csr"
    private val preferences by getPreferencesLazy()

    override val client = network.client.newBuilder()
        .addInterceptor(ImageInterceptor())
        .build()

    override fun popularMangaRequest(page: Int): Request = rankingRequest()

    override fun popularMangaParse(response: Response): MangasPage {
        val mangas = response.parseAsProto<PopularResponse>().rankingLists.firstOrNull()?.toSMangaList().orEmpty()
        return MangasPage(mangas, false)
    }

    override fun latestUpdatesRequest(page: Int): Request = updateDayRequest(getLatestDay())

    override fun latestUpdatesParse(response: Response): MangasPage {
        val titles = response.parseAsProto<TitleListView>().list?.titles.orEmpty()
        val mangas = titles.map { it.toSManga() }
        return MangasPage(mangas, false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val url = "$baseUrl/search".toHttpUrl().newBuilder()
                .addQueryParameter("keyword", query)
                .build()
            return GET(url, headers)
        }

        return when (val category = filters.firstInstance<CategoryFilter>().value) {
            in WEEKDAYS -> updateDayRequest(category)
            "completed", "one-shot" -> GET("$baseUrl/rensai/$category", headers)
            else -> rankingRequest(category)
        }
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val url = response.request.url
        return when {
            url.pathSegments.any { it in HTML_PATHS } -> seriesListParse(response)
            url.queryParameter("day") != null -> latestUpdatesParse(response)
            else -> rankingParse(response)
        }
    }

    private fun seriesListParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.grid > a[href^='/title/']").map {
            SManga.create().apply {
                setUrlWithoutDomain(it.absUrl("href").toHttpUrl().pathSegments.last())
                title = it.selectFirst("p.text-black")?.text() ?: it.selectFirst("p")!!.text()
                thumbnail_url = it.selectFirst("img")?.absUrl("src")
            }
        }
        return MangasPage(mangas, false)
    }

    private fun rankingParse(response: Response): MangasPage {
        val (id, type) = response.request.url.fragment!!.split(":")
        val mangas = response.parseAsProto<PopularResponse>().rankingLists.firstOrNull { it.tag.id == id && it.tag.type == type }?.toSMangaList().orEmpty()
        return MangasPage(mangas, false)
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        val url = apiUrl.toHttpUrl().newBuilder()
            .addQueryParameter("rq", "title/detail")
            .addQueryParameter("title_id", manga.url)
            .build()
        return GET(url, headers)
    }

    override fun mangaDetailsParse(response: Response): SManga = response.parseAsProto<TitleDetailView>().toSManga()

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/title/${manga.url}"

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        val chapters = response.parseAsProto<TitleDetailView>().chapters
            .filter { !hideLocked || !it.isLocked }
            .map { it.toSChapter() }

        val first = chapters.first()
        val last = chapters.last()

        val isAscending = when {
            first.date_upload != last.date_upload -> first.date_upload < last.date_upload
            first.chapter_number > -1 && last.chapter_number > -1 -> first.chapter_number < last.chapter_number
            else -> first.url.toLong() < last.url.toLong()
        }
        return if (isAscending) chapters.reversed() else chapters
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/chapter/${chapter.url}"

    override fun pageListRequest(chapter: SChapter): Request {
        val url = apiUrl.toHttpUrl().newBuilder()
            .addQueryParameter("rq", "chapter/viewer")
            .addQueryParameter("chapter_id", chapter.url)
            .build()

        return Request.Builder()
            .url(url)
            .headers(headers)
            .put(ViewerRequest().toRequestBodyProto())
            .build()
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = try {
            response.parseAsProto<ViewerView>()
        } catch (_: Exception) {
            throw Exception("Log in via WebView and purchase this chapter to read.")
        }

        return result.pages.mapIndexed { i, img ->
            val imageUrl = img.url.toHttpUrl().newBuilder()
                .fragment("keys=${result.aesKey}:${result.aesIv}")
                .build()
                .toString()
            Page(i, imageUrl = imageUrl)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_LOCKED_PREF_KEY
            title = "Hide Paid Chapters"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    // Filter
    override fun getFilterList() = FilterList(
        CategoryFilter(),
    )

    private fun rankingRequest(category: String? = null): Request {
        val url = apiUrl.toHttpUrl().newBuilder()
            .addQueryParameter("rq", "title/list/ranking")
            .apply { category?.let { fragment(it) } }
            .build()
        return GET(url, headers)
    }

    private fun updateDayRequest(day: String): Request {
        val url = apiUrl.toHttpUrl().newBuilder()
            .addQueryParameter("rq", "title/list/update_day")
            .addQueryParameter("day", day)
            .build()
        return GET(url, headers)
    }

    private fun getLatestDay(): String {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Tokyo"))
        val days = arrayOf("sun", "mon", "tue", "wed", "thu", "fri", "sat")
        return days[calendar.get(Calendar.DAY_OF_WEEK) - 1]
    }

    companion object {
        private const val HIDE_LOCKED_PREF_KEY = "hide_locked"
        private val WEEKDAYS = setOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")
        private val HTML_PATHS = setOf("search", "completed", "one-shot")
    }
}
