package eu.kanade.tachiyomi.extension.en.vizshonenjump

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getString
import keiyoushi.utils.getStringOrNull
import keiyoushi.utils.obj
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.CacheControl
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class Viz :
    KeiSource(),
    ConfigurableSource {
    private val preferences by getPreferencesLazy()
    private val servicePath get() = if (name.contains("Shonen Jump")) "shonenjump" else "vizmanga"
    private val searchPath get() = if (name.contains("Shonen Jump")) "SjChapterSeries" else "VmChapterSeries"
    private val subscriber get() = if (name.contains("Shonen Jump")) "is_sj_subscriber" else "is_vm_subscriber"

    private var loggedIn: Boolean? = null

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor(ImageInterceptor())
        addInterceptor {
            val request = it.request()
            val response = it.proceed(request)
            if (response.request.url.encodedPath == "/$servicePath") {
                throw IOException("This service is not available in your country.")
            }
            response
        }
    }

    // ============================== Popular ==============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val response = client.get("$baseUrl/manga-books/$servicePath/section/trending-manga", cacheControl = CacheControl.FORCE_NETWORK)
        return parseMangaPage(response)
    }

    private fun parseMangaPage(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.o_sortable > a.o_chapters-link").sortedBy { it.parent()?.attr("data-sort-recent")?.toInt() }.map(::mangaFromElement)
        return MangasPage(mangas, false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val response = client.get("$baseUrl/manga-books/$servicePath/section/free-chapters", cacheControl = CacheControl.FORCE_NETWORK)
        return parseMangaPage(response)
    }

    // ============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("search", query)
            .addQueryParameter("category", searchPath)
            .build()
        val document = client.get(url, cacheControl = CacheControl.FORCE_NETWORK).asJsoup()
        val mangas = document.select("div.p-cs-tile a.o_property-link").map(::mangaFromElement)
            .filter { manga -> manga.title.contains(query, true) }
        return MangasPage(mangas, false)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val service = url.pathSegments[0]
        val seriesSlug = url.pathSegments[2]

        if (service != servicePath) return null

        val manga = SManga.create().apply {
            this.url = seriesSlug
        }

        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false)
            .manga
            .apply {
                initialized = true
            }
    }

    private fun mangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst("div.pad-x-rg")!!.text()
        thumbnail_url = element.selectFirst("div.pos-r img.disp-bl")?.absUrl("data-original")
        setUrlWithoutDomain(element.absUrl("href").toHttpUrl().pathSegments[2])
    }

    // ============================== Updates ==============================

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/$servicePath/chapters/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String {
        val chapterId = chapter.memo.getStringOrNull("id")
            ?: throw Exception("Refresh chapter list")
        val slug = chapter.memo.getString("slug")
        return "$baseUrl/$servicePath/$slug/chapter/$chapterId"
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), cacheControl = CacheControl.FORCE_NETWORK).asJsoup()

        val seriesIntro = document.selectFirst("section#series-intro")!!
        val sManga = SManga.create().apply {
            url = manga.url
            title = seriesIntro.selectFirst("h2")!!.text()
            author = seriesIntro.selectFirst("span.disp-bl--bm")?.text()?.replace("Created by ", "")
            description = seriesIntro.selectFirst("h2 + div")?.text()
            thumbnail_url = document.selectFirst("meta[property=og:image]")?.absUrl("content")
                ?: document.selectFirst("section.section_chapters td a > img")?.absUrl("data-original")
        }

        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)

        val elements = document.select("section.section_chapters a.o_chapter-container[id^=ch-]")
        if (elements.isEmpty()) {
            if (document.selectFirst("section.section_static") != null) {
                throw Exception("This service is not available in your country.")
            }
        }

        val isSubscriber = checkIfIsLoggedIn()

        val chapterList = elements.mapNotNull {
            val urlStr = it.absUrl("data-target-url")
            if (urlStr.isBlank()) return@mapNotNull null

            val isMarkupLocked = urlStr.startsWith("javascript")
            val isLocked = isMarkupLocked && !isSubscriber
            if (hideLocked && isLocked) return@mapNotNull null

            val lock = if (isLocked) "🔒 " else ""
            val dateTable = it.selectFirst("div:nth-child(1) table")

            SChapter.create().apply {
                if (dateTable == null) {
                    name = lock + it.text()
                } else {
                    name = lock + (it.selectFirst("div:nth-child(2) table")?.selectFirst("td")?.text() ?: "Oneshot")
                    dateTable.selectFirst("td[align=right], td > span")?.text()?.let { dateStr ->
                        date_upload = runCatching {
                            LocalDate.parse(dateStr, DATE_FORMATTER).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                        }.getOrDefault(0L)
                    }
                }

                chapter_number = name.substringAfter("Ch. ").substringBefore(':').trim().toFloatOrNull() ?: -1F
                val cleanUrl = if (isMarkupLocked) urlStr.substringAfter(",'").substringBeforeLast("'") else urlStr
                val absoluteUrl = if (cleanUrl.startsWith("http")) cleanUrl else "$baseUrl$cleanUrl"
                val paths = absoluteUrl.toHttpUrl().pathSegments
                url = "${paths[3]}#${paths[1]}"
                memo = buildJsonObject {
                    put("id", paths[3])
                    put("slug", paths[1])
                }
            }
        }.sortedByDescending { it.chapter_number }

        return SMangaUpdate(sManga, chapterList)
    }

    // =============================== Pages ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        val pageCount = document.selectFirst("script:containsData(var pages)")!!.data()
            .substringAfter("= ")
            .substringBefore(";")
            .toInt()

        checkIfIsLoggedIn()
        val chapterId = chapter.memo.getStringOrNull("id")
            ?: throw Exception("Refresh chapter list")
        val hasAccess = client.newCall(pageUrlRequest(chapterId, "0")).execute().parseAs<Dto>().ok
        if (hasAccess == 0) {
            throw Exception("Log in via WebView and subscribe to the website's service.")
        }

        return (0..pageCount).map {
            Page(it, "$baseUrl/$chapterId#$it")
        }
    }

    override suspend fun getImageUrl(page: Page): String {
        val parts = page.url.toHttpUrl()
        val chapterId = parts.pathSegments.first()
        val index = parts.fragment!!
        val response = client.newCall(pageUrlRequest(chapterId, index)).awaitSuccess()
        val result = response.parseAs<Dto>()
        return "${result.data.obj.values.first().string}#scramble"
    }

    // ============================= Utilities =============================

    private fun pageUrlRequest(chapterId: String, index: String): Request {
        val login = if (loggedIn == true) "active" else "false"
        val newHeaders = headersBuilder()
            .set("X-Client-Login", login)
            .build()

        val pageUrl = "$baseUrl/manga/get_manga_url".toHttpUrl().newBuilder()
            .addQueryParameter("device_id", "3")
            .addQueryParameter("manga_id", chapterId)
            .addQueryParameter("pages", index)
            .build()

        return GET(pageUrl, newHeaders, CacheControl.FORCE_NETWORK)
    }

    private val subcription = Regex("""var $subscriber\s*=\s*(true|false)""")

    private suspend fun checkIfIsLoggedIn(): Boolean = try {
        val document = client.get("$baseUrl/account/refresh_login_links").asJsoup()
        loggedIn = document.selectFirst("div#o_account-links-content")
            ?.attr("logged_in")?.toBoolean() ?: false

        document.selectFirst("script:containsData($subscriber)")?.data()
            ?.let { subcription.find(it) }
            ?.groupValues?.get(1)?.toBoolean() ?: false
    } catch (_: Exception) {
        loggedIn = false
        false
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_LOCKED_PREF_KEY
            title = "Hide Locked Chapters"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    companion object {
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH)
        private const val HIDE_LOCKED_PREF_KEY = "hide_locked"
    }
}
