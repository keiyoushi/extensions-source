package eu.kanade.tachiyomi.extension.ja.flowercomics

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
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferencesLazy
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.util.Calendar
import java.util.TimeZone

class FlowerComics :
    HttpSource(),
    ConfigurableSource {
    override val name = "Flower Comics"
    override val baseUrl = "https://flowercomics.jp"
    override val lang = "ja"
    override val supportsLatest = true

    private val preferences: SharedPreferences by getPreferencesLazy()
    private val jst = TimeZone.getTimeZone("Asia/Tokyo")
    private val rscHeaders = headersBuilder()
        .set("rsc", "1")
        .build()

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(ImageInterceptor())
        .build()

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/ranking", rscHeaders)

    override fun popularMangaParse(response: Response): MangasPage {
        val data = response.extractNextJs<List<RankingBlock>>()
        val mangas = data.orEmpty().firstOrNull { it.rankingTypeName == "総合" }?.titles.orEmpty().map { it.toSManga() }
        return MangasPage(mangas, false)
    }

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/rensai", rscHeaders)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val data = response.extractNextJs<LatestData>()
        val calendar = Calendar.getInstance(jst)
        val weekdays = listOf("sun", "mon", "tue", "wed", "thu", "fri", "sat")
        val day = weekdays[calendar.get(Calendar.DAY_OF_WEEK) - 1]
        val mangas = data?.weekdays[day].orEmpty().map { it.toSManga() }
        return MangasPage(mangas, false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val url = "$baseUrl/search".toHttpUrl().newBuilder()
                .addQueryParameter("keyword", query)
                .build()
            return GET(url, headers)
        }

        val filter = filters.firstInstance<CategoryFilter>()
        return when (filter.type) {
            "day" -> {
                val url = "$baseUrl/rensai".toHttpUrl().newBuilder()
                    .fragment(filter.value)
                    .build()
                GET(url, rscHeaders).newBuilder().tag(filter.type).build()
            }
            "rensai" -> {
                GET("$baseUrl/rensai/${filter.value}", headers)
            }
            else -> {
                GET("$baseUrl/tag/${filter.value}/${filter.type}", headers)
            }
        }
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val tag = response.request.tag()
        if (tag == "day") {
            val day = response.request.url.fragment
            val data = response.extractNextJs<LatestData>()
            val mangas = data?.weekdays?.get(day).orEmpty().map { it.toSManga() }
            return MangasPage(mangas, false)
        }

        val document = response.asJsoup()
        val mangas = document.select("div.grid > a[href^=/title/]").map {
            SManga.create().apply {
                val seriesId = (it.absUrl("href")).toHttpUrl().pathSegments.last()
                setUrlWithoutDomain(seriesId)
                title = it.selectFirst("p.text-black")!!.text()
                thumbnail_url = it.selectFirst("img")?.absUrl("src")
            }
        }
        return MangasPage(mangas, false)
    }

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$baseUrl/title/${manga.url}", headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("h1")!!.text()
            author = document.select("a[href^=/author/] p").joinToString { it.text() }
            description = document.selectFirst("div.whitespace-pre-wrap p")?.text()
            genre = document.select("ul[aria-label=ジャンルタグ一覧] p").joinToString { it.text() }
            thumbnail_url = document.selectFirst("section img.object-cover")?.absUrl("src")
            val statusText = document.select("div.bg-main-blue p").text()
            status = when {
                statusText.contains("完結") -> SManga.COMPLETED
                statusText.contains("更新予定") || statusText.contains("連載") -> SManga.ONGOING
                else -> SManga.UNKNOWN
            }
        }
    }

    override fun chapterListRequest(manga: SManga): Request = GET("$baseUrl/title/${manga.url}", rscHeaders)

    override fun chapterListParse(response: Response): List<SChapter> {
        val data = response.extractNextJs<EntryChapters>()
        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        val allChapters = data?.earlyChapters?.plus(data.omittedMiddleChapters)?.plus(data.latestChapters)
        return allChapters.orEmpty()
            .filter { !hideLocked || !it.isLocked }
            .map { it.toSChapter() }
    }

    override fun pageListRequest(chapter: SChapter): Request = GET("$baseUrl/chapter/${chapter.url}/viewer", rscHeaders)

    override fun pageListParse(response: Response): List<Page> {
        val data = response.extractNextJs<List<PageEntry>>() ?: throw Exception("Log in via WebView and rent or purchase this chapter.")
        return data.filter { it.crypto != null }.mapIndexed { i, page ->
            val url = page.src.toHttpUrl().newBuilder()
                .fragment("${page.crypto?.key}:${page.crypto?.iv}")
                .build()
                .toString()
            Page(i, imageUrl = url)
        }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_LOCKED_PREF_KEY
            title = "Hide Locked Chapters"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    override fun getFilterList(): FilterList = FilterList(CategoryFilter())

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    companion object {
        private const val HIDE_LOCKED_PREF_KEY = "hide_locked"
    }
}
