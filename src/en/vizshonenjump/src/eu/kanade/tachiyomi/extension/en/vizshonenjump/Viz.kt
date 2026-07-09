package eu.kanade.tachiyomi.extension.en.vizshonenjump

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
import keiyoushi.network.rateLimit
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.obj
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import keiyoushi.utils.tryParse
import okhttp3.CacheControl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import rx.Observable
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class Viz :
    HttpSource(),
    ConfigurableSource {
    private val preferences by getPreferencesLazy()
    private val servicePath get() = if (name.contains("Shonen Jump")) "shonenjump" else "vizmanga"
    private val searchPath get() = if (name.contains("Shonen Jump")) "SjChapterSeries" else "VmChapterSeries"

    private var loggedIn: Boolean? = null

    override val supportsLatest = true

    override val client = network.client.newBuilder()
        .addInterceptor(ImageInterceptor())
        .addInterceptor {
            val request = it.request()
            val response = it.proceed(request)
            if (response.request.url.encodedPath == "/$servicePath") {
                throw IOException("This service is not available in your country.")
            }
            response
        }
        .rateLimit(1, 1.seconds) { it.host == baseUrl.toHttpUrl().host }
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Origin", baseUrl)
        .add("Referer", "$baseUrl/")

    // ============================== Popular ==============================

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/manga-books/$servicePath/section/trending-manga", headers, CacheControl.FORCE_NETWORK)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.o_sortable > a.o_chapters-link").sortedBy { it.parent()?.attr("data-sort-recent")?.toInt() }.map(::mangaFromElement)
        return MangasPage(mangas, false)
    }

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/manga-books/$servicePath/section/free-chapters", headers, CacheControl.FORCE_NETWORK)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // ============================== Search ===============================

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith("https://")) {
            val url = query.toHttpUrlOrNull() ?: throw Exception("Unsupported Url")
            if (url.host != baseUrl.toHttpUrl().host) throw Exception("Unsupported Url")

            val pathSegments = url.pathSegments
            if (pathSegments.size < 3) throw Exception("Unsupported url")

            val service = pathSegments[0]
            val seriesSlug = if (pathSegments[2] == "chapter" && pathSegments[1].contains("-chapter-")) {
                pathSegments[1].substringBeforeLast("-chapter-")
            } else {
                pathSegments[2]
            }
            return fetchSearchManga(page, "$PREFIX_URL_SEARCH/$service/chapters/$seriesSlug", filters)
        }

        if (query.startsWith(PREFIX_URL_SEARCH)) {
            val pathSegments = query.substringAfter(PREFIX_URL_SEARCH).split("/")
            val service = pathSegments[1]
            val seriesSlug = pathSegments[3]

            if (service != servicePath) return Observable.just(MangasPage(emptyList(), false))
            return fetchMangaDetails(
                SManga.create().apply {
                    url = seriesSlug
                    initialized = true
                },
            ).map { MangasPage(listOf(it), false) }
        }

        return super.fetchSearchManga(page, query, filters).map {
            MangasPage(it.mangas.filter { manga -> manga.title.contains(query, true) }, it.hasNextPage)
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("search", query)
            .addQueryParameter("category", searchPath)
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.p-cs-tile a.o_property-link").map(::mangaFromElement)
        return MangasPage(mangas, false)
    }

    private fun mangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst("div.pad-x-rg")!!.text()
        thumbnail_url = element.selectFirst("div.pos-r img.disp-bl")?.absUrl("data-original")
        setUrlWithoutDomain(element.absUrl("href").toHttpUrl().pathSegments[2])
    }

    // ============================== Details ==============================

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/$servicePath/chapters/${manga.url}"

    override fun mangaDetailsRequest(manga: SManga): Request = GET(getMangaUrl(manga), headers, CacheControl.FORCE_NETWORK)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val seriesIntro = document.selectFirst("section#series-intro")!!
        return SManga.create().apply {
            url = response.request.url.pathSegments[2]
            title = seriesIntro.selectFirst("h2")!!.text()
            author = seriesIntro.selectFirst("span.disp-bl--bm")?.text()?.replace("Created by ", "")
            description = seriesIntro.selectFirst("h2 + div")?.text()
            thumbnail_url = document.selectFirst("meta[property=og:image]")?.absUrl("content")
                ?: document.selectFirst("section.section_chapters td a > img")?.absUrl("data-original")
        }
    }

    // ============================= Chapters ==============================

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        val document = response.asJsoup()
        val elements = document.select("section.section_chapters a.o_chapter-container[id^=ch-]")
        if (elements.isEmpty()) {
            if (document.selectFirst("section.section_static") != null) {
                throw Exception("This service is not available in your country.")
            }
        }

        return elements.mapNotNull {
            val urlStr = it.absUrl("data-target-url")
            if (urlStr.isBlank()) return@mapNotNull null

            val isLocked = urlStr.startsWith("javascript")
            if (hideLocked && isLocked) return@mapNotNull null

            val lock = if (isLocked) "🔒 " else ""
            val dateTable = it.selectFirst("div:nth-child(1) table")

            SChapter.create().apply {
                if (dateTable == null) {
                    name = lock + it.text()
                } else {
                    name = lock + (it.selectFirst("div:nth-child(2) table")?.selectFirst("td")?.text() ?: "Oneshot")
                    date_upload = DATE_FORMATTER.tryParse(dateTable.selectFirst("td[align=right], td > span")?.text())
                }

                chapter_number = name.substringAfter("Ch. ").substringBefore(':').trim().toFloatOrNull() ?: -1F
                val cleanUrl = if (isLocked) urlStr.substringAfter(",'").substringBeforeLast("'") else urlStr
                val absoluteUrl = if (cleanUrl.startsWith("http")) cleanUrl else "$baseUrl$cleanUrl"
                val paths = absoluteUrl.toHttpUrl().pathSegments
                url = "${paths[3]}#${paths[1]}"
            }
        }.sortedByDescending { it.chapter_number }
    }

    // =============================== Pages ===============================

    override fun getChapterUrl(chapter: SChapter): String {
        val parts = "$baseUrl/${chapter.url}".toHttpUrl()
        val chapterId = parts.pathSegments.first()
        val slug = parts.fragment
        return "$baseUrl/$servicePath/$slug/chapter/$chapterId"
    }

    override fun pageListRequest(chapter: SChapter): Request = GET(getChapterUrl(chapter), headers)

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val pageCount = document.selectFirst("script:containsData(var pages)")!!.data()
            .substringAfter("= ")
            .substringBefore(";")
            .toInt()

        checkIfIsLoggedIn()
        val login = if (loggedIn == true) "active" else "false"
        val newHeaders = headersBuilder()
            .set("X-Client-Login", login)
            .build()

        val chapterId = response.request.url.pathSegments.last()
        val pages = (0..pageCount).joinToString(",")
        val pageUrl = "$baseUrl/manga/get_manga_url".toHttpUrl().newBuilder()
            .addQueryParameter("device_id", "3")
            .addQueryParameter("manga_id", chapterId)
            .addQueryParameter("pages", pages)
            .build()

        val result = client.newCall(GET(pageUrl, newHeaders, CacheControl.FORCE_NETWORK)).execute().parseAs<Dto>()
        if (result.ok == 0) {
            throw Exception("Log in via WebView and subscribe to the website's service.")
        }

        return result.data.obj.toSortedMap().map { (index, image) ->
            Page(index.toInt(), imageUrl = "${image.string}#scramble")
        }
    }

    // ============================= Utilities =============================

    private fun checkIfIsLoggedIn() {
        val loginCheckRequest = GET("$baseUrl/account/refresh_login_links", headers)
        try {
            val document = network.client.newCall(loginCheckRequest).execute().asJsoup()
            loggedIn = document.selectFirst("div#o_account-links-content")
                ?.attr("logged_in")?.toBoolean() ?: false
        } catch (_: Exception) {
            loggedIn = false
        }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_LOCKED_PREF_KEY
            title = "Hide Locked Chapters"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    companion object {
        private val DATE_FORMATTER = SimpleDateFormat("MMMM d, yyyy", Locale.ENGLISH)
        private const val PREFIX_URL_SEARCH = "url:"
        private const val HIDE_LOCKED_PREF_KEY = "hide_locked"
    }
}
