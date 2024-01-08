package eu.kanade.tachiyomi.extension.ko.newtoki

import android.content.SharedPreferences
import android.util.Log
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * NewToki Source
 *
 * Based on https://github.com/gnuboard/gnuboard5
 **/
abstract class NewToki(
    override val name: String,
    private val boardName: String,
    private val preferences: SharedPreferences,
) : ParsedHttpSource(), ConfigurableSource {

    override val lang: String = "ko"
    override val supportsLatest = true

    override val client by lazy { buildClient(withRateLimit = false) }
    private val rateLimitedClient by lazy { buildClient(withRateLimit = true) }

    private fun buildClient(withRateLimit: Boolean) =
        network.cloudflareClient.newBuilder()
            .apply { if (withRateLimit) rateLimit(1, preferences.rateLimitPeriod.toLong()) }
            .addInterceptor(DomainInterceptor) // not rate-limited
            .connectTimeout(10, TimeUnit.SECONDS) // fail fast
            .build()

    override fun popularMangaSelector() = "div#webtoon-list > ul > li"

    override fun popularMangaFromElement(element: Element): SManga {
        val linkElement = element.getElementsByTag("a").first()!!

        val manga = SManga.create()
        manga.url = getUrlPath(linkElement.attr("href"))
        manga.title = element.select("span.title").first()!!.ownText()
        manga.thumbnail_url = linkElement.getElementsByTag("img").attr("src")
        return manga
    }

    override fun popularMangaNextPageSelector() = "ul.pagination > li:last-child:not(.disabled)"

    // Do not add page parameter if page is 1 to prevent tracking.
    override fun popularMangaRequest(page: Int) = GET("$baseUrl/$boardName" + if (page > 1) "/p$page" else "", headers)

    override fun searchMangaSelector() = popularMangaSelector()
    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return if (query.startsWith(PREFIX_ID_SEARCH)) {
            val realQuery = query.removePrefix(PREFIX_ID_SEARCH)
            val urlPath = "/$boardName/$realQuery"
            rateLimitedClient.newCall(GET("$baseUrl$urlPath", headers))
                .asObservableSuccess()
                .map { response ->
                    // the id is matches any of 'post' from their CMS board.
                    // Includes Manga Details Page, Chapters, Comments, and etcs...
                    actualMangaParseById(urlPath, response)
                }
        } else {
            super.fetchSearchManga(page, query, filters)
        }
    }

    private fun actualMangaParseById(urlPath: String, response: Response): MangasPage {
        val document = response.asJsoup()

        // Only exists on detail page.
        val firstChapterButton = document.select("tr > th > button.btn-blue").first()
        // only exists on chapter with proper manga detail page.
        val fullListButton = document.select(".comic-navbar .toon-nav a").last()

        val list: List<SManga> = when {
            firstChapterButton?.text()?.contains("첫회보기") == true -> { // Check this page is detail page
                val details = mangaDetailsParse(document)
                details.url = urlPath
                listOf(details)
            }
            fullListButton?.text()?.contains("전체목록") == true -> { // Check this page is chapter page
                val url = fullListButton.attr("abs:href")
                val details = mangaDetailsParse(rateLimitedClient.newCall(GET(url, headers)).execute())
                details.url = getUrlPath(url)
                listOf(details)
            }
            else -> emptyList()
        }

        return MangasPage(list, false)
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val info = document.select("div.view-title > .view-content").first()!!
        val title = document.select("div.view-content > span > b").text()
        val thumbnail = document.select("div.row div.view-img > img").attr("src")
        val descriptionElement = info.select("div.row div.view-content:not([style])")
        val description = descriptionElement.map {
            it.text().trim()
        }
        val prefix = if (isCleanPath(document.location())) "" else needMigration()

        val manga = SManga.create()
        manga.title = title
        manga.description = description.joinToString("\n", prefix = prefix)
        manga.thumbnail_url = thumbnail
        descriptionElement.forEach {
            val text = it.text()
            when {
                "작가" in text -> manga.author = it.getElementsByTag("a").text()
                "분류" in text -> {
                    val genres = mutableListOf<String>()
                    it.getElementsByTag("a").forEach { item ->
                        genres.add(item.text())
                    }
                    manga.genre = genres.joinToString(", ")
                }
                "발행구분" in text -> manga.status = parseStatus(it.getElementsByTag("a").text())
            }
        }
        return manga
    }

    private fun parseStatus(status: String) = when (status.trim()) {
        "주간", "격주", "월간", "격월/비정기", "단행본" -> SManga.ONGOING
        "단편", "완결" -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = "div.serial-list > ul.list-body > li.list-item"

    override fun chapterFromElement(element: Element): SChapter {
        val linkElement = element.select(".wr-subject > a.item-subject").last()!!
        val rawName = linkElement.ownText().trim()

        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(linkElement.attr("href"))
        chapter.chapter_number = parseChapterNumber(rawName)
        chapter.name = rawName
        chapter.date_upload = parseChapterDate(element.select(".wr-date").last()!!.text().trim())
        return chapter
    }

    private fun parseChapterNumber(name: String): Float {
        try {
            if (name.contains("[단편]")) return 1f
            // `특별` means `Special`, so It can be buggy. so pad `편`(Chapter) to prevent false return
            if (name.contains("번외") || name.contains("특별편")) return -2f
            val regex = chapterNumberRegex
            val (ch_primal, ch_second) = regex.find(name)!!.destructured
            return (ch_primal + if (ch_second.isBlank()) "" else ".$ch_second").toFloatOrNull()
                ?: -1f
        } catch (e: Exception) {
            Log.e("NewToki", "failed to parse chapter number '$name'", e)
            return -1f
        }
    }

    private fun mangaDetailsParseWithTitleCheck(manga: SManga, document: Document) =
        mangaDetailsParse(document).apply {
            // TODO: don't throw when there is download folder rename feature
            if (manga.description.isNullOrEmpty() && manga.title != title) {
                throw Exception(titleNotMatch(title))
            }
        }

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return rateLimitedClient.newCall(mangaDetailsRequest(manga))
            .asObservableSuccess()
            .map { response ->
                val document = response.asJsoup()
                mangaDetailsParseWithTitleCheck(manga, document).apply { initialized = true }
            }
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return rateLimitedClient.newCall(chapterListRequest(manga))
            .asObservableSuccess()
            .map { response ->
                val document = response.asJsoup()
                val title = mangaDetailsParseWithTitleCheck(manga, document).title
                document.select(chapterListSelector()).map {
                    chapterFromElement(it).apply {
                        name = name.removePrefix(title).trimStart()
                    }
                }
            }
    }

    // not thread-safe
    private val dateFormat by lazy { SimpleDateFormat("yyyy.MM.dd", Locale.ENGLISH) }

    private fun parseChapterDate(date: String): Long {
        return try {
            if (date.contains(":")) {
                val calendar = Calendar.getInstance()
                val splitDate = date.split(":")

                val hours = splitDate.first().toInt()
                val minutes = splitDate.last().toInt()

                val calendarHours = calendar.get(Calendar.HOUR)
                val calendarMinutes = calendar.get(Calendar.MINUTE)

                if (calendarHours >= hours && calendarMinutes > minutes) {
                    calendar.add(Calendar.DATE, -1)
                }

                calendar.timeInMillis
            } else {
                dateFormat.parse(date)?.time ?: 0
            }
        } catch (e: Exception) {
            Log.e("NewToki", "failed to parse chapter date '$date'", e)
            0
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        val script = document.select("script:containsData(html_data)").firstOrNull()?.data()
            ?: throw Exception("data script not found")
        val loadScript = document.select("script:containsData(data_attribute)").firstOrNull()?.data()
            ?: throw Exception("load script not found")
        val dataAttr = "abs:data-" + loadScript.substringAfter("data_attribute: '").substringBefore("',")

        return htmlDataRegex.findAll(script).map { it.groupValues[1] }
            .asIterable()
            .flatMap { it.split(".") }
            .joinToString("") { it.toIntOrNull(16)?.toChar()?.toString() ?: "" }
            .let { Jsoup.parse(it) }
            .select("img[src=/img/loading-image.gif], .view-img > img[itemprop]")
            .mapIndexed { i, img -> Page(i, "", if (img.hasAttr(dataAttr)) img.attr(dataAttr) else img.attr("abs:content")) }
    }

    override fun latestUpdatesSelector() = ".media.post-list"
    override fun latestUpdatesFromElement(element: Element) = ManaToki.latestUpdatesElementParse(element)
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/page/update?hid=update&page=$page", headers)
    override fun latestUpdatesNextPageSelector() = ".pg_end"

    // We are able to get the image URL directly from the page list
    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException("This method should not be called!")

    override fun setupPreferenceScreen(screen: androidx.preference.PreferenceScreen) {
        getPreferencesInternal(screen.context).map(screen::addPreference)
    }

    protected fun getUrlPath(orig: String): String {
        val url = baseUrl.toHttpUrl().resolve(orig) ?: return orig
        val pathSegments = url.pathSegments
        return "/${pathSegments[0]}/${pathSegments[1]}"
    }

    private fun isCleanPath(absUrl: String): Boolean {
        val url = absUrl.toHttpUrl()
        return url.pathSegments.size == 2 && url.querySize == 0 && url.fragment == null
    }

    companion object {
        const val PREFIX_ID_SEARCH = "id:"

        private val chapterNumberRegex by lazy { Regex("([0-9]+)(?:[-.]([0-9]+))?화") }
        private val htmlDataRegex by lazy { Regex("""html_data\+='([^']+)'""") }
    }
}
