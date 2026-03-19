package eu.kanade.tachiyomi.extension.ja.alphapolis

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
import okhttp3.HttpUrl.Builder
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import rx.Observable
import java.net.URLDecoder

class Alphapolis :
    HttpSource(),
    ConfigurableSource {
    override val name = "Alphapolis"
    override val baseUrl = "https://www.alphapolis.co.jp"
    override val lang = "ja"
    override val supportsLatest = true
    override val versionId = 2

    private var xsrfToken: String? = null
    private val preferences: SharedPreferences by getPreferencesLazy()

    // Load proper thumbnails
    override fun headersBuilder() = super.headersBuilder()
        .set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36")
        .add("X-Requested-With", "XMLHttpRequest")

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor { chain ->
            val response = chain.proceed(chain.request())
            response.headers("Set-Cookie")
                .firstOrNull { it.startsWith("XSRF-TOKEN=") }
                ?.substringAfter("XSRF-TOKEN=")
                ?.substringBefore(";")
                ?.let { URLDecoder.decode(it, "UTF-8") }
                ?.also { xsrfToken = it }

            if (response.code == 419) {
                response.close()
                val token = getXsrfToken() ?: throw Exception("XSRF-Token not found")
                xsrfToken = token
                val newRequest = chain.request().newBuilder()
                    .header("X-XSRF-TOKEN", token)
                    .build()
                chain.proceed(newRequest)
            } else {
                response
            }
        }
        .build()

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/manga/official/ranking?category=total", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".official-manga-sub-like_ranking--list, .official-manga-sub-like_ranking--panel").map {
            SManga.create().apply {
                setUrlWithoutDomain(it.absUrl("href"))
                title = it.selectFirst(".official-manga-sub-like_ranking--list_title, .official-manga-sub-like_ranking--panel_title")!!.text()
                val thumb = it.selectFirst("img, .official-manga-sub-like_ranking--panel_thumbnail")
                thumbnail_url = thumb?.absUrl("data-src")?.ifEmpty { thumb.absUrl("data-bg") }
            }
        }
        return MangasPage(mangas, false)
    }

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/manga/official/search?page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = searchMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        fun Builder.addFilter(param: String, filter: Filter.Group<FilterTag>) = filter.state.filter { it.state }.forEachIndexed { i, option -> addQueryParameter("$param[$i]", option.value) }
        fun Builder.addFilter(param: String, value: String, filter: Filter.CheckBox) = apply { if (filter.state) addQueryParameter(param, value) }

        val url = "$baseUrl/manga/official/search".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .apply {
                if (query.isNotBlank()) addQueryParameter("query", query)
                addFilter("category", filters.firstInstance<CategoryFilter>())
                addFilter("label", filters.firstInstance<LabelFilter>())
                addFilter("complete", filters.firstInstance<StatusFilter>())
                addFilter("rental", filters.firstInstance<RentalFilter>())
                addFilter("is_free_daily", "enable", filters.firstInstance<DailyFreeFilter>())
            }
        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".mangas-list .official-manga-panel > a").map {
            SManga.create().apply {
                setUrlWithoutDomain(it.absUrl("href"))
                title = it.selectFirst(".title")!!.text()
                thumbnail_url = it.selectFirst(".panel")?.absUrl("data-bg")
            }
        }
        val hasNextPage = document.selectFirst("i.fa.fa-angle-double-right") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst(".manga-detail-description > .title > h1")!!.text()
            val authors = document.select(".manga-detail-description .author-label .authors .mangaka").toList()
            author = authors.filter { it.text().contains("原作") }
                .mapNotNull { it.selectFirst("a")?.text() }
                .joinToString()
            artist = authors.filter { it.text().contains("漫画") }
                .mapNotNull { it.selectFirst("a")?.text() }
                .joinToString()
            description = document.selectFirst(".manga-detail-outline .outline")?.text()
            genre = document.select(".manga-detail-tags .official-manga-tags .official-manga-tag").joinToString { it.text() }
            status = when (document.selectFirst(".wrap-content-status a[href*=complete]")?.text()) {
                "連載中" -> SManga.ONGOING
                "完結" -> SManga.COMPLETED
                "休載中" -> SManga.ON_HIATUS
                else -> SManga.UNKNOWN
            }
            thumbnail_url = document.selectFirst(".manga-bigbanner img")?.absUrl("src")
        }
    }

    override fun chapterListRequest(manga: SManga): Request {
        val mangaId = (baseUrl + manga.url).toHttpUrl().pathSegments.last().toInt()
        val body = ChapterRequestBody(mangaId).toJsonString().toRequestBody(MEDIA_TYPE)
        return POST("$baseUrl/manga/official/episodes.json", xsrfHeaders(), body)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val result = response.parseAs<ChapterResponse>()
        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        return result.episodes.filter { !hideLocked || !it.isLocked }.map { it.toSChapter(baseUrl) }.reversed()
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val parts = "$baseUrl/${chapter.url}".toHttpUrl()
        val mangaId = parts.pathSegments.first().toInt()
        val episodeId = parts.fragment!!.toInt()
        return "$baseUrl/manga/official/$mangaId/$episodeId"
    }

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        return Observable.fromCallable {
            val parts = "$baseUrl/${chapter.url}".toHttpUrl()
            val mangaId = parts.pathSegments.first().toInt()
            val episodeId = parts.fragment!!.toInt()
            val viewerUrl = "$baseUrl/manga/official/viewer.json"
            val newHeaders = xsrfHeaders(getChapterUrl(chapter))

            fun getPages(resolution: String): List<Page> {
                val body = ViewerRequestBody(episodeId, false, mangaId, false, resolution).toJsonString().toRequestBody(MEDIA_TYPE)
                val request = POST(viewerUrl, newHeaders, body)

                return client.newCall(request).execute().use {
                    it.parseAs<ViewerResponse>().page?.images?.mapIndexed { i, img ->
                        Page(i, imageUrl = img.url)
                    } ?: throw Exception("Log in via WebView and rent or purchase this chapter to read.")
                }
            }

            val resolutions = listOf("full_hd", "standard")
            val pages = resolutions.asSequence().map { getPages(it) }.firstOrNull { it.isNotEmpty() }
                ?: throw Exception("Log in via WebView and rent or purchase this chapter to read.")

            pages
        }
    }

    private fun requireXsrfToken() = xsrfToken ?: getXsrfToken() ?: throw Exception("XSRF-Token not found")

    private fun xsrfHeaders(referer: String? = null) = headersBuilder()
        .set("X-XSRF-TOKEN", requireXsrfToken())
        .apply { if (referer != null) set("Referer", referer) }
        .build()

    private fun getXsrfToken(): String? {
        val cookies = client.cookieJar.loadForRequest(baseUrl.toHttpUrl())
        return cookies.firstOrNull { it.name == "XSRF-TOKEN" }?.value?.let {
            URLDecoder.decode(it, "UTF-8")
        }
    }

    override fun pageListRequest(chapter: SChapter): Request = throw UnsupportedOperationException()
    override fun pageListParse(response: Response): List<Page> = throw UnsupportedOperationException()
    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // Filters
    override fun getFilterList() = FilterList(
        Filter.Header("Note: Search and active filters are applied together"),
        CategoryFilter(),
        LabelFilter(),
        StatusFilter(),
        RentalFilter(),
        Filter.Separator(),
        DailyFreeFilter(),
    )

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_LOCKED_PREF_KEY
            title = "Hide Locked Chapters"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    companion object {
        private val MEDIA_TYPE = "application/json".toMediaType()
        private const val HIDE_LOCKED_PREF_KEY = "hide_locked"
    }
}
