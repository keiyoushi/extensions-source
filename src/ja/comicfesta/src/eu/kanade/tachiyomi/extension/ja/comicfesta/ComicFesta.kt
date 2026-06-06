package eu.kanade.tachiyomi.extension.ja.comicfesta

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.lib.clipstudioreader.ClipStudioReader
import keiyoushi.lib.cookieinterceptor.CookieInterceptor
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.getPreferencesLazy
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

class ComicFesta :
    ClipStudioReader(),
    ConfigurableSource {
    override val name = "Comic Festa"
    private val domain = "comic.iowl.jp"
    override val baseUrl = "https://$domain"
    override val lang = "ja"
    override val supportsLatest = true

    private val preferences by getPreferencesLazy()
    private val rscHeaders = headersBuilder()
        .add("rsc", "1")
        .build()

    override val client = super.client.newBuilder()
        .addInterceptor(CookieInterceptor(domain, listOf("checked_age" to "1", "sp_display" to "1", "cf_checked_age_guest" to "1", "cf_checked_age" to "1")))
        .addInterceptor {
            val request = it.request()
            val response = it.proceed(request)
            if (request.url.pathSegments.first() == "volumes" && (response.request.url.pathSegments.first() == "entry" || response.request.url.pathSegments.first() == "error")) {
                throw IOException("Log in via WebView and purchase this product to read.")
            }
            response
        }
        .build()

    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/sales_rankings/monthly_general".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, rscHeaders)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.extractNextJs<RankingResponse>()
        val mangas = result?.titles.orEmpty().map { it.toSManga() }
        val hasNextPage = response.request.url.queryParameter("page") != "2"
        return MangasPage(mangas, hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/titles".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("sort", "release")
            .addQueryParameter("search_form[other_item][]", "new")
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = searchMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/titles".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("search_form[keyword]", query)
            .addQueryParameter("search", query)
            .addQueryParameter("commit", "search")
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.list-detail-box").map {
            SManga.create().apply {
                title = it.selectFirst("div.title-box")!!.text()
                thumbnail_url = it.selectFirst("img")?.absUrl("src")
                setUrlWithoutDomain(it.selectFirst(".list-left-box a")!!.absUrl("href").toHttpUrl().pathSegments.last())
            }
        }
        val hasNextPage = document.selectFirst("a[rel=next]") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$baseUrl/titles/${manga.url}", headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("h1[class*='titleName']")!!.text()
            author = document.select("a[href*='/authors/']").joinToString { it.text() }
            description = document.selectFirst("div[class*='description']")?.text()
            genre = document.select("[class*='outlineTag'] a").joinToString { it.text() }
            thumbnail_url = document.selectFirst("img[class*='thumbnail']")?.absUrl("src")
            val statusText = document.select("[class*='latest-package-num-display']").text()
            status = if (statusText.contains("完結")) SManga.COMPLETED else SManga.ONGOING
        }
    }

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga) // no redirect for r18 content with rsc

    override fun chapterListParse(response: Response): List<SChapter> {
        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        val result = response.extractNextJs<ChapterListResponse>()
        return result?.toSChapterList(hideLocked).orEmpty().reversed()
    }

    override fun pageListRequest(chapter: SChapter): Request = GET("$baseUrl/volumes/${chapter.url}", headers)

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
