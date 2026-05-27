package eu.kanade.tachiyomi.extension.en.philiascans

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
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.jsonInstance
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class PhiliaScans :
    HttpSource(),
    ConfigurableSource {
    override val name = "Philia Scans"
    override val baseUrl = "https://philiascans.org"
    override val lang = "en"
    override val supportsLatest = true

    // Bumped to force migration as the URL structures changed from /read/ to /series/
    override val versionId = 4

    private val preferences by getPreferencesLazy()

    override val client = network.client.newBuilder()
        .addInterceptor(::metaRedirectInterceptor)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_SHOW_LOCKED_CHAPS_KEY
            title = "Show locked/premium chapters"
            summary = "Show chapters that require coins to read. Hidden by default."
            setDefaultValue(PREF_SHOW_LOCKED_CHAPS_DEFAULT)
        }.also(screen::addPreference)
    }

    private fun metaRedirectInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        if (response.isSuccessful && response.header("Content-Type")?.contains("text/html") == true) {
            val peekedBody = response.peekBody(2 * 1024 * 1024).string()
            val redirectMatch = metaRedirectRegex.find(peekedBody)

            if (redirectMatch != null) {
                val newUrl = redirectMatch.groupValues[1].replace("&amp;", "&")
                response.close()
                val newRequest = request.newBuilder()
                    .url(newUrl.let { if (it.startsWith("/")) baseUrl + it else it })
                    .build()
                return chain.proceed(newRequest)
            }
        }
        return response
    }

    // ============================== Popular ==============================
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/all-mangas?m_orderby=views&m_order=desc&page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("a.manga-card, a[href^=\"/series/\"]").mapNotNull { element ->
            val absoluteUrl = element.attr("abs:href")
            val httpUrl = absoluteUrl.toHttpUrlOrNull() ?: return@mapNotNull null
            val path = httpUrl.encodedPath
            val parts = path.trimEnd('/').split("/")
            if (parts.size != 3 || parts[1] != "series") return@mapNotNull null // Expecting ["", "series", "manga-slug"]

            val img = element.selectFirst("img")
            val titleEl = element.selectFirst(".card-title, h3, h2")

            val parsedTitle = titleEl?.text()?.takeIf { it.isNotEmpty() }
                ?: img?.attr("alt")?.trim()?.takeIf { it.isNotEmpty() }
                ?: element.ownText()

            val lowerTitle = parsedTitle.lowercase()
            if (parsedTitle.isEmpty() || lowerTitle == "read" || lowerTitle == "read now" || lowerTitle.startsWith("chapter")) {
                return@mapNotNull null
            }

            SManga.create().apply {
                setUrlWithoutDomain(absoluteUrl)
                title = parsedTitle
                thumbnail_url = img?.let { it.attr("abs:data-src").ifBlank { it.attr("abs:src") } }?.let { cleanImageUrl(it) }
            }
        }.distinctBy { it.url }

        val hasNextPage = document.selectFirst(".philia-pagination a[rel=next], a.page-link[rel=next]") != null

        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/all-mangas?m_orderby=recently-updated&m_order=desc&page=$page", headers)

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    // ============================== Search ===============================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/all-mangas".toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) {
                addQueryParameter("s", query)
            }
            addQueryParameter("page", page.toString())

            filters.forEach { filter ->
                when (filter) {
                    is TypeFilter -> {
                        filter.state.filter { it.state }.forEach {
                            addQueryParameter("filter_type[]", it.value)
                        }
                    }
                    is GenreFilter -> {
                        filter.state.filter { it.state }.forEach {
                            addQueryParameter("filter_genre[]", it.value)
                        }
                    }
                    is StatusFilter -> {
                        filter.state.filter { it.state }.forEach {
                            addQueryParameter("filter_status[]", it.value)
                        }
                    }
                    is ContentRatingFilter -> {
                        filter.state.filter { it.state }.forEach {
                            addQueryParameter("filter_rating[]", it.value)
                        }
                    }
                    is SortFilter -> {
                        val sortables = arrayOf("recently-updated", "trending", "views", "rating", "title", "added")
                        val orderby = sortables[filter.state?.index ?: 0]
                        val order = if (filter.state?.ascending == true) "asc" else "desc"
                        addQueryParameter("m_orderby", orderby)
                        addQueryParameter("m_order", order)
                    }
                    else -> {}
                }
            }
        }.build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    // ============================== Details ==============================
    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("h1, .detail-title")!!.text()
            description = document.selectFirst("#synopsis-wrap, .synopsis, .description, [class*=synopsis], [class*=description]")?.text()
            thumbnail_url = document.selectFirst(".detail-cover img, .manga-card-cover img")?.let { it.attr("abs:data-src").ifBlank { it.attr("abs:src") } }?.let { cleanImageUrl(it) }
                ?: document.selectFirst("img[src*=/covers/], img[src*=/media/]")?.attr("abs:src")?.let { cleanImageUrl(it) }

            val detailsText = document.select("main").text().lowercase()
            status = when {
                "ongoing" in detailsText || "releasing" in detailsText || "on going" in detailsText -> SManga.ONGOING
                "completed" in detailsText -> SManga.COMPLETED
                "hiatus" in detailsText || "on hold" in detailsText -> SManga.ON_HIATUS
                "canceled" in detailsText || "cancelled" in detailsText -> SManga.CANCELLED
                else -> SManga.UNKNOWN
            }

            author = document.selectFirst("span.info-key:contains(Author) + span.info-val")?.text()
            artist = document.selectFirst("span.info-key:contains(Artist) + span.info-val")?.text()

            genre = document.select("a.detail-genre-tag, a[href*=\"/genre/\"], a[href*=\"?filter_genre=\"]").joinToString { it.text() }
        }
    }

    // ============================= Chapters ==============================
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val mangaUrl = response.request.url.encodedPath.removeSuffix("/")

        // Try extracting via Next.js RSC Payload for speed and robustness
        val chaptersDto = document.extractNextJs<List<Dto>>()

        if (!chaptersDto.isNullOrEmpty()) {
            val showLocked = preferences.getBoolean(PREF_SHOW_LOCKED_CHAPS_KEY, PREF_SHOW_LOCKED_CHAPS_DEFAULT)
            return chaptersDto
                .filter { showLocked || it.coinPrice == 0 }
                .map { it.toSChapter(mangaUrl) }
        }

        // Fallback to DOM parsing if DTO fails
        val chapters = mutableListOf<SChapter>()
        val chapterElements = document.select("#philia-chapters-list a.chapter-item, .chapters-list a.chapter-item, a.chapter-row-link")
        val showLocked = preferences.getBoolean(PREF_SHOW_LOCKED_CHAPS_KEY, PREF_SHOW_LOCKED_CHAPS_DEFAULT)

        chapterElements.forEach { a ->
            val isLocked = a.hasClass("premium-item") ||
                a.select(".chapter-crown, .premium-lock").isNotEmpty() ||
                a.closest(".premium-block") != null

            if (isLocked && !showLocked) {
                return@forEach
            }

            val absoluteUrl = a.attr("abs:href")

            val chapter = SChapter.create().apply {
                setUrlWithoutDomain(absoluteUrl)

                val num = a.select(".chapter-num").text()
                val parsedTitle = a.select(".chapter-title").text()

                name = if (num.isNotEmpty() && parsedTitle.isNotEmpty() && num != parsedTitle && parsedTitle != num.replace("Ch.", "").trim()) {
                    "$num - $parsedTitle"
                } else {
                    num.ifBlank {
                        parsedTitle.ifBlank {
                            a.text().replace(chapterCleanupRegex, "").trim().ifBlank { "Chapter" }
                        }
                    }
                }

                if (isLocked) {
                    name += " 🔒"
                }

                val row = a.closest(".chapter-row") ?: a.parent()
                val time = row?.select("time")?.first()
                if (time != null && time.hasAttr("datetime")) {
                    date_upload = isoDateFormat.tryParse(time.attr("datetime"))
                } else {
                    val text = row?.select(".chapter-date, .time, .date, [class*=time], [class*=date]")?.text()
                        ?.takeIf { it.isNotEmpty() } ?: row?.text() ?: ""
                    date_upload = parseRelativeDate(text)
                }
            }
            chapters.add(chapter)
        }

        return chapters.distinctBy { it.url }
    }

    // =============================== Pages ===============================
    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val pagesMap = mutableMapOf<Int, String>()

        document.select("main img, #chapter-content img, .reader-images img, #ch-images img, .reading-content img, .reader-images-container img").forEachIndexed { index, img ->
            val url = img.attr("abs:data-src").ifBlank { img.attr("abs:src") }
            if (url.isNotBlank()) {
                val clean = cleanImageUrl(url)
                val lower = clean.lowercase()
                if ("avatar" !in lower && "logo" !in lower && "favicon" !in lower && "og-default" !in lower) {
                    val pageNum = img.attr("data-page").toIntOrNull() ?: (index + 1)
                    pagesMap[pageNum] = clean
                }
            }
        }

        val totalPagesStr = document.selectFirst("#total-pages")?.text()?.substringAfter("/")?.trim()
        val totalPages = totalPagesStr?.toIntOrNull() ?: 0

        // Fallback for RSC/Next.js JSON payloads where pages are lazy-loaded.
        if (pagesMap.isEmpty() || pagesMap.size < totalPages) {
            val flightString = buildString {
                document.select("script:not([src])").forEach { script ->
                    val data = script.data()
                    if ("self.__next_f.push" !in data) return@forEach

                    val match = nextFlightRegex.find(data) ?: return@forEach
                    try {
                        val arr = jsonInstance.parseToJsonElement(match.groupValues[1]).jsonArray
                        val content = arr.getOrNull(1)?.jsonPrimitive?.contentOrNull ?: return@forEach
                        append(content)
                    } catch (_: Exception) {}
                }
            }

            flightString.split('\n').forEach { line ->
                val colonIdx = line.indexOf(':')
                if (colonIdx != -1) {
                    val jsonStr = line.substring(colonIdx + 1)
                    if (jsonStr.startsWith("[") || jsonStr.startsWith("{")) {
                        try {
                            val element = jsonInstance.parseToJsonElement(jsonStr)
                            element.findPages(pagesMap)
                        } catch (_: Exception) {}
                    }
                }
            }
        }

        if (pagesMap.isEmpty()) return emptyList()

        return pagesMap.toSortedMap().values.mapIndexed { i, url -> Page(i, imageUrl = url) }
    }

    private fun JsonElement.findPages(map: MutableMap<Int, String>) {
        when (this) {
            is JsonObject -> {
                val src = this["src"]?.jsonPrimitive?.contentOrNull
                val pageNumber = this["pageNumber"]?.jsonPrimitive?.intOrNull
                if (src != null && pageNumber != null && src.contains("media")) {
                    map[pageNumber] = cleanImageUrl(src)
                }
                values.forEach { it.findPages(map) }
            }
            is JsonArray -> {
                forEach { it.findPages(map) }
            }
            else -> {}
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Filters ==============================
    override fun getFilterList() = FilterList(
        SortFilter(),
        TypeFilter(),
        StatusFilter(),
        ContentRatingFilter(),
        GenreFilter(),
    )

    // ============================= Utilities =============================
    private fun cleanImageUrl(url: String): String {
        var cleanUrl = url
        if (cleanUrl.contains("/_next/image")) {
            val encodedUrl = cleanUrl.substringAfter("url=").substringBefore("&")
            cleanUrl = java.net.URLDecoder.decode(encodedUrl, "UTF-8")
        }
        if (cleanUrl.startsWith("/")) {
            cleanUrl = baseUrl + cleanUrl
        }
        return try {
            cleanUrl.toHttpUrl().newBuilder().removeAllQueryParameters("w").build().toString()
        } catch (_: Exception) {
            cleanUrl
        }
    }

    private fun parseRelativeDate(text: String): Long {
        val trimmed = text.trim()
        val now = System.currentTimeMillis()
        if (trimmed.contains("just now", true)) return now

        val match = relativeDateRegex.find(trimmed)
        if (match != null) {
            val amount = match.groupValues[1].toLongOrNull() ?: return 0L
            val unit = match.groupValues[2].lowercase()
            val millis = when (unit) {
                "m" -> amount * 60 * 1000L
                "h" -> amount * 60 * 60 * 1000L
                "d" -> amount * 24 * 60 * 60 * 1000L
                "w" -> amount * 7 * 24 * 60 * 60 * 1000L
                "y" -> amount * 365 * 24 * 60 * 60 * 1000L
                else -> 0L
            }
            return now - millis
        }

        val dateMatch = shortDateRegex.find(trimmed)
        if (dateMatch != null) {
            return fallbackDateFormat.tryParse(dateMatch.groupValues[1])
        }

        val customMatch2 = customDateRegex.find(trimmed)
        if (customMatch2 != null) {
            val datePart = customMatch2.groupValues[1]
            val yearPart = customMatch2.groupValues[2].takeIf { it.isNotBlank() } ?: Calendar.getInstance().get(Calendar.YEAR).toString()
            val timePart = customMatch2.groupValues[3]
            val formattedStr = "$datePart $yearPart $timePart"
            return customDateFormat.tryParse(formattedStr)
        }

        return 0L
    }

    companion object {
        private const val PREF_SHOW_LOCKED_CHAPS_KEY = "pref_show_locked_chaps"
        private const val PREF_SHOW_LOCKED_CHAPS_DEFAULT = false

        private val metaRedirectRegex = Regex("""<meta\s+id="__next-page-redirect"\s+http-equiv="refresh"\s+content="[^"]*url=([^"]+)"""")
        private val relativeDateRegex = Regex("""(\d+)\s*([mhdwyMHDWY])(?:in|ins|our|ours|ay|ays|eek|eeks|ear|ears)?\s+ago""")
        private val shortDateRegex = Regex("""\b(\d{1,2}/\d{1,2}/\d{4})\b""")
        private val customDateRegex = Regex("""(\d{1,2}/[A-Za-z]{3})(?:/(\d{4}))?\s+(\d{1,2}:\d{2}\s+(?:am|pm|AM|PM))""")
        private val chapterCleanupRegex = Regex("""(?i)\b(free|premium|EN)\b""")

        // Used to fetch the independent React Flight payload strings from script tags safely.
        private val nextFlightRegex = Regex("""self\.__next_f\.push\(\s*(\[.*])\s*\)\s*;?\s*$""", RegexOption.DOT_MATCHES_ALL)
        private val fallbackDateFormat = SimpleDateFormat("M/d/yyyy", Locale.US)
        private val customDateFormat = SimpleDateFormat("dd/MMM yyyy h:mm a", Locale.ENGLISH)
        private val isoDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }
}
