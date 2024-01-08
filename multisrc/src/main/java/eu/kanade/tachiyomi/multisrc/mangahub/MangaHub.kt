package eu.kanade.tachiyomi.multisrc.mangahub

import eu.kanade.tachiyomi.lib.randomua.UserAgentType
import eu.kanade.tachiyomi.lib.randomua.setRandomUserAgent
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.Cookie
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.net.URLEncoder
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

abstract class MangaHub(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
    private val dateFormat: SimpleDateFormat = SimpleDateFormat("MM-dd-yyyy", Locale.US),
) : ParsedHttpSource() {

    override val supportsLatest = true

    private var baseApiUrl = "https://api.mghubcdn.com"
    private var baseCdnUrl = "https://imgx.mghubcdn.com"

    override val client: OkHttpClient = super.client.newBuilder()
        .setRandomUserAgent(
            userAgentType = UserAgentType.DESKTOP,
            filterInclude = listOf("chrome"),
        )
        .addInterceptor(::apiAuthInterceptor)
        .rateLimit(1)
        .build()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9")
        .add("Accept-Language", "en-US,en;q=0.5")
        .add("DNT", "1")
        .add("Referer", "$baseUrl/")
        .add("Sec-Fetch-Dest", "document")
        .add("Sec-Fetch-Mode", "navigate")
        .add("Sec-Fetch-Site", "same-origin")
        .add("Upgrade-Insecure-Requests", "1")

    open val json: Json by injectLazy()

    private fun apiAuthInterceptor(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        val cookie = client.cookieJar
            .loadForRequest(baseUrl.toHttpUrl())
            .firstOrNull { it.name == "mhub_access" && it.value.isNotEmpty() }

        val request =
            if (originalRequest.url.toString() == "$baseApiUrl/graphql" && cookie != null) {
                originalRequest.newBuilder()
                    .header("x-mhub-access", cookie.value)
                    .build()
            } else {
                originalRequest
            }

        return chain.proceed(request)
    }

    private fun refreshApiKey(chapter: SChapter) {
        val now = Calendar.getInstance().time.time

        val slug = "$baseUrl${chapter.url}"
            .toHttpUrlOrNull()
            ?.pathSegments
            ?.get(1)

        val url = if (slug != null) {
            "$baseUrl/manga/$slug".toHttpUrl()
        } else {
            baseUrl.toHttpUrl()
        }

        // Clear key cookie
        val cookie = Cookie.parse(url, "mhub_access=; Max-Age=0; Path=/")!!
        client.cookieJar.saveFromResponse(url, listOf(cookie))

        // Set required cookie (for cache busting?)
        val recently = buildJsonObject {
            putJsonObject((now - (0..3600).random()).toString()) {
                put("mangaID", (1..42_000).random())
                put("number", (1..20).random())
            }
        }.toString()

        client.cookieJar.saveFromResponse(
            url,
            listOf(
                Cookie.Builder()
                    .domain(url.host)
                    .name("recently")
                    .value(URLEncoder.encode(recently, "utf-8"))
                    .expiresAt(now + 2 * 60 * 60 * 24 * 31) // +2 months
                    .build(),
            ),
        )

        val request = GET("$url?reloadKey=1", headers)
        client.newCall(request).execute()
    }

    // popular
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/popular/page/$page", headers)
    }

    override fun popularMangaSelector() = ".col-sm-6:not(:has(a:contains(Yaoi)))"

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            setUrlWithoutDomain(element.select("h4 a").attr("abs:href"))
            title = element.select("h4 a").text()
            thumbnail_url = element.select("img").attr("abs:src")
        }
    }

    override fun popularMangaNextPageSelector() = "ul.pager li.next > a"

    // latest
    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/updates/page/$page", headers)
    }

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga {
        return popularMangaFromElement(element)
    }

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    // search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search/page/$page".toHttpUrlOrNull()!!.newBuilder()
        url.addQueryParameter("q", query)
        (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
            when (filter) {
                is OrderBy -> {
                    val order = filter.values[filter.state]
                    url.addQueryParameter("order", order.key)
                }
                is GenreList -> {
                    val genre = filter.values[filter.state]
                    url.addQueryParameter("genre", genre.key)
                }
                else -> {}
            }
        }
        return GET(url.toString(), headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga {
        return popularMangaFromElement(element)
    }

    // not sure if this still works, some duplicates i found is also using different thumbnail_url
    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        /*
         * To remove duplicates we group by the thumbnail_url, which is
         * common between duplicates. The duplicates have a suffix in the
         * url "-by-{name}". Here we select the shortest url, to avoid
         * removing manga that has "by" in the title already.
         * Example:
         * /manga/tales-of-demons-and-gods (kept)
         * /manga/tales-of-demons-and-gods-by-mad-snail (removed)
         * /manga/leveling-up-by-only-eating (kept)
         */
        val mangas = document.select(searchMangaSelector()).map { element ->
            searchMangaFromElement(element)
        }.groupBy { it.thumbnail_url }.mapValues { (_, values) ->
            values.minByOrNull { it.url.length }!!
        }.values.toList()

        val hasNextPage = searchMangaNextPageSelector().let { selector ->
            document.select(selector).first()
        } != null

        return MangasPage(mangas, hasNextPage)
    }

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // manga details
    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        manga.title = document.select(".breadcrumb .active span").text()
        manga.author = document.select("div:has(h1) span:contains(Author) + span").first()?.text()
        manga.artist = document.select("div:has(h1) span:contains(Artist) + span").first()?.text()
        manga.genre = document.select(".row p a").joinToString { it.text() }
        manga.description = document.select(".tab-content p").first()?.text()
        manga.thumbnail_url = document.select("img.img-responsive").first()
            ?.attr("src")

        document.select("div:has(h1) span:contains(Status) + span").first()?.text()?.also { statusText ->
            when {
                statusText.contains("ongoing", true) -> manga.status = SManga.ONGOING
                statusText.contains("completed", true) -> manga.status = SManga.COMPLETED
                else -> manga.status = SManga.UNKNOWN
            }
        }

        // add alternative name to manga description
        document.select("h1 small").firstOrNull()?.ownText()?.let { alternativeName ->
            if (alternativeName.isNotBlank()) {
                manga.description = manga.description.orEmpty().let {
                    if (it.isBlank()) {
                        "Alternative Name: $alternativeName"
                    } else {
                        "$it\n\nAlternative Name: $alternativeName"
                    }
                }
            }
        }

        return manga
    }

    // chapters
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val head = document.head()
        return document.select(chapterListSelector()).map { chapterFromElement(it, head) }
    }

    override fun chapterListSelector() = ".tab-content ul li"

    private fun chapterFromElement(element: Element, head: Element): SChapter {
        val chapter = SChapter.create()
        val potentialLinks = element.select("a[href*='$baseUrl/chapter/']:not([rel*=nofollow]):not([rel*=noreferrer])")
        var visibleLink = ""
        potentialLinks.forEach { a ->
            val className = a.className()
            val styles = head.select("style").html()
            if (!styles.contains(".$className { display:none; }")) {
                visibleLink = a.attr("href")
                return@forEach
            }
        }
        chapter.setUrlWithoutDomain(visibleLink)
        chapter.name = chapter.url.trimEnd('/').substringAfterLast('/').replace('-', ' ')
        chapter.date_upload = element.select("small.UovLc").first()?.text()?.let { parseChapterDate(it) } ?: 0
        return chapter
    }

    override fun chapterFromElement(element: Element): SChapter {
        throw UnsupportedOperationException("Not Used")
    }

    private fun parseChapterDate(date: String): Long {
        val now = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        var parsedDate = 0L
        when {
            "just now" in date || "less than an hour" in date -> {
                parsedDate = now.timeInMillis
            }
            // parses: "1 hour ago" and "2 hours ago"
            "hour" in date -> {
                val hours = date.replaceAfter(" ", "").trim().toInt()
                parsedDate = now.apply { add(Calendar.HOUR, -hours) }.timeInMillis
            }
            // parses: "Yesterday" and "2 days ago"
            "day" in date -> {
                val days = date.replace("days ago", "").trim().toIntOrNull() ?: 1
                parsedDate = now.apply { add(Calendar.DAY_OF_YEAR, -days) }.timeInMillis
            }
            // parses: "2 weeks ago"
            "weeks" in date -> {
                val weeks = date.replace("weeks ago", "").trim().toInt()
                parsedDate = now.apply { add(Calendar.WEEK_OF_YEAR, -weeks) }.timeInMillis
            }
            // parses: "12-20-2019" and defaults everything that wasn't taken into account to 0
            else -> {
                try {
                    parsedDate = dateFormat.parse(date)?.time ?: 0L
                } catch (e: ParseException) { /*nothing to do, parsedDate is initialized with 0L*/ }
            }
        }
        return parsedDate
    }

    // pages
    override fun pageListRequest(chapter: SChapter): Request {
        val body = buildJsonObject {
            put("query", PAGES_QUERY)
            put(
                "variables",
                buildJsonObject {
                    val mangaSource = when (name) {
                        "MangaHub" -> "m01"
                        "MangaReader.site" -> "mr01"
                        "MangaPanda.onl" -> "mr02"
                        else -> null
                    }
                    val chapterUrl = chapter.url.split("/")

                    put("mangaSource", mangaSource)
                    put("slug", chapterUrl[2])
                    put("number", chapterUrl[3].substringAfter("-").toFloat())
                },
            )
        }
            .toString()
            .toRequestBody()

        val newHeaders = headersBuilder()
            .set("Accept", "application/json")
            .set("Content-Type", "application/json")
            .set("Origin", baseUrl)
            .set("Sec-Fetch-Dest", "empty")
            .set("Sec-Fetch-Mode", "cors")
            .set("Sec-Fetch-Site", "cross-site")
            .removeAll("Upgrade-Insecure-Requests")
            .build()

        return POST("$baseApiUrl/graphql", newHeaders, body)
    }

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> =
        super.fetchPageList(chapter)
            .doOnError { refreshApiKey(chapter) }
            .retry(1)

    override fun pageListParse(document: Document): List<Page> = throw UnsupportedOperationException("Not used")
    override fun pageListParse(response: Response): List<Page> {
        val chapterObject = json.decodeFromString<ApiChapterPagesResponse>(response.body.string())

        if (chapterObject.data?.chapter == null) {
            if (chapterObject.errors != null) {
                val errors = chapterObject.errors.joinToString("\n") { it.message }
                throw Exception(errors)
            }
            throw Exception("Unknown error while processing pages")
        }

        val pages = json.decodeFromString<ApiChapterPages>(chapterObject.data.chapter.pages)

        return pages.i.mapIndexed { i, page ->
            Page(i, "", "$baseCdnUrl/${pages.p}$page")
        }
    }

    // Image
    override fun imageUrlRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .set("Accept", "image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
            .set("Sec-Fetch-Dest", "image")
            .set("Sec-Fetch-Mode", "no-cors")
            .set("Sec-Fetch-Site", "cross-site")
            .removeAll("Upgrade-Insecure-Requests")
            .build()

        return GET(page.url, newHeaders)
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used")

    // filters
    private class Genre(title: String, val key: String) : Filter.TriState(title) {
        override fun toString(): String {
            return name
        }
    }

    private class Order(title: String, val key: String) : Filter.TriState(title) {
        override fun toString(): String {
            return name
        }
    }

    private class OrderBy(orders: Array<Order>) : Filter.Select<Order>("Order", orders, 0)
    private class GenreList(genres: Array<Genre>) : Filter.Select<Genre>("Genres", genres, 0)

    override fun getFilterList() = FilterList(
        OrderBy(orderBy),
        GenreList(genres),
    )

    private val orderBy = arrayOf(
        Order("Popular", "POPULAR"),
        Order("Updates", "LATEST"),
        Order("A-Z", "ALPHABET"),
        Order("New", "NEW"),
        Order("Completed", "COMPLETED"),
    )

    private val genres = arrayOf(
        Genre("All Genres", "all"),
        Genre("[no chapters]", "no-chapters"),
        Genre("4-Koma", "4-koma"),
        Genre("Action", "action"),
        Genre("Adventure", "adventure"),
        Genre("Award Winning", "award-winning"),
        Genre("Comedy", "comedy"),
        Genre("Cooking", "cooking"),
        Genre("Crime", "crime"),
        Genre("Demons", "demons"),
        Genre("Doujinshi", "doujinshi"),
        Genre("Drama", "drama"),
        Genre("Ecchi", "ecchi"),
        Genre("Fantasy", "fantasy"),
        Genre("Food", "food"),
        Genre("Game", "game"),
        Genre("Gender bender", "gender-bender"),
        Genre("Harem", "harem"),
        Genre("Historical", "historical"),
        Genre("Horror", "horror"),
        Genre("Isekai", "isekai"),
        Genre("Josei", "josei"),
        Genre("Kids", "kids"),
        Genre("Magic", "magic"),
        Genre("Magical Girls", "magical-girls"),
        Genre("Manhua", "manhua"),
        Genre("Manhwa", "manhwa"),
        Genre("Martial arts", "martial-arts"),
        Genre("Mature", "mature"),
        Genre("Mecha", "mecha"),
        Genre("Medical", "medical"),
        Genre("Military", "military"),
        Genre("Music", "music"),
        Genre("Mystery", "mystery"),
        Genre("One shot", "one-shot"),
        Genre("Oneshot", "oneshot"),
        Genre("Parody", "parody"),
        Genre("Police", "police"),
        Genre("Psychological", "psychological"),
        Genre("Romance", "romance"),
        Genre("School life", "school-life"),
        Genre("Sci fi", "sci-fi"),
        Genre("Seinen", "seinen"),
        Genre("Shotacon", "shotacon"),
        Genre("Shoujo", "shoujo"),
        Genre("Shoujo ai", "shoujo-ai"),
        Genre("Shoujoai", "shoujoai"),
        Genre("Shounen", "shounen"),
        Genre("Shounen ai", "shounen-ai"),
        Genre("Shounenai", "shounenai"),
        Genre("Slice of life", "slice-of-life"),
        Genre("Smut", "smut"),
        Genre("Space", "space"),
        Genre("Sports", "sports"),
        Genre("Super Power", "super-power"),
        Genre("Superhero", "superhero"),
        Genre("Supernatural", "supernatural"),
        Genre("Thriller", "thriller"),
        Genre("Tragedy", "tragedy"),
        Genre("Vampire", "vampire"),
        Genre("Webtoon", "webtoon"),
        Genre("Webtoons", "webtoons"),
        Genre("Wuxia", "wuxia"),
        Genre("Yuri", "yuri"),
    )
}
