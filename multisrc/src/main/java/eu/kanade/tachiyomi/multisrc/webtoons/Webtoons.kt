package eu.kanade.tachiyomi.multisrc.webtoons

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter.Header
import eu.kanade.tachiyomi.source.model.Filter.Select
import eu.kanade.tachiyomi.source.model.Filter.Separator
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.net.SocketException
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

open class Webtoons(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
    open val langCode: String = lang,
    open val localeForCookie: String = lang,
    private val dateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH),
) : ParsedHttpSource() {

    override val supportsLatest = true

    override val client: OkHttpClient = super.client.newBuilder()
        .cookieJar(
            object : CookieJar {
                override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {}
                override fun loadForRequest(url: HttpUrl): List<Cookie> {
                    return listOf<Cookie>(
                        Cookie.Builder()
                            .domain("www.webtoons.com")
                            .path("/")
                            .name("ageGatePass")
                            .value("true")
                            .name("locale")
                            .value(localeForCookie)
                            .name("needGDPR")
                            .value("false")
                            .build(),
                    )
                }
            },
        )
        .addInterceptor(::sslRetryInterceptor)
        .build()

    // m.webtoons.com throws an SSL error that can be solved by a simple retry
    private fun sslRetryInterceptor(chain: Interceptor.Chain): Response {
        return try {
            chain.proceed(chain.request())
        } catch (e: SocketException) {
            chain.proceed(chain.request())
        }
    }

    private val day: String
        get() {
            return when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
                Calendar.SUNDAY -> "div._list_SUNDAY"
                Calendar.MONDAY -> "div._list_MONDAY"
                Calendar.TUESDAY -> "div._list_TUESDAY"
                Calendar.WEDNESDAY -> "div._list_WEDNESDAY"
                Calendar.THURSDAY -> "div._list_THURSDAY"
                Calendar.FRIDAY -> "div._list_FRIDAY"
                Calendar.SATURDAY -> "div._list_SATURDAY"
                else -> {
                    "div"
                }
            }
        }

    val json: Json by injectLazy()

    override fun popularMangaSelector() = "not using"

    override fun latestUpdatesSelector() = "div#dailyList > $day li > a"

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "https://www.webtoons.com/$langCode/")

    protected val mobileHeaders: Headers = super.headersBuilder()
        .add("Referer", "https://m.webtoons.com")
        .build()

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/$langCode/dailySchedule", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val mangas = mutableListOf<SManga>()
        val document = response.asJsoup()
        var maxChild = 0

        // For ongoing webtoons rows are ordered by descending popularity, count how many rows there are
        document.select("div#dailyList .daily_section").forEach { day ->
            day.select("li").count().let { rowCount ->
                if (rowCount > maxChild) maxChild = rowCount
            }
        }

        // Process each row
        for (i in 1..maxChild) {
            document.select("div#dailyList .daily_section li:nth-child($i) a").map { mangas.add(popularMangaFromElement(it)) }
        }

        // Add completed webtoons, no sorting needed
        document.select("div.daily_lst.comp li a").map { mangas.add(popularMangaFromElement(it)) }

        return MangasPage(mangas.distinctBy { it.url }, false)
    }

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/$langCode/dailySchedule?sortOrder=UPDATE&webtoonCompleteType=ONGOING", headers)

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()

        manga.setUrlWithoutDomain(element.attr("href"))
        manga.title = element.select("p.subj").text()
        manga.thumbnail_url = element.select("img").attr("abs:src")

        return manga
    }

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun popularMangaNextPageSelector(): String? = null

    override fun latestUpdatesNextPageSelector(): String? = null

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (!query.startsWith(URL_SEARCH_PREFIX)) {
            return super.fetchSearchManga(page, query, filters)
        }

        val emptyResult = Observable.just(MangasPage(emptyList(), false))

        // given a url to either a webtoon or an episode, returns a url path to corresponding webtoon
        fun webtoonPath(u: HttpUrl) = when {
            langCode == u.pathSegments[0] -> "/${u.pathSegments[0]}/${u.pathSegments[1]}/${u.pathSegments[2]}/list"
            else -> "/${u.pathSegments[0]}/${u.pathSegments[1]}/list" // dongmanmanhua doesn't include langCode
        }

        return query.substringAfter(URL_SEARCH_PREFIX).toHttpUrlOrNull()?.let { url ->
            val title_no = url.queryParameter("title_no")
            val couldBeWebtoonOrEpisode = title_no != null && (url.pathSegments.size >= 3 && url.pathSegments.last().isNotEmpty())
            val isThisLang = "$url".startsWith("$baseUrl/$langCode")
            if (!(couldBeWebtoonOrEpisode && isThisLang)) {
                emptyResult
            } else {
                val potentialUrl = "${webtoonPath(url)}?title_no=$title_no"
                fetchMangaDetails(SManga.create().apply { this.url = potentialUrl }).map {
                    it.url = potentialUrl
                    MangasPage(listOf(it), false)
                }
            }
        } ?: emptyResult
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/$langCode/search?keyword=$query".toHttpUrlOrNull()?.newBuilder()!!
        val uriPart = (filters.find { it is SearchType } as? SearchType)?.toUriPart() ?: ""

        url.addQueryParameter("searchType", uriPart)
        if (uriPart != "WEBTOON" && page > 1) url.addQueryParameter("page", page.toString())

        return GET(url.toString(), headers)
    }

    override fun searchMangaSelector() = "#content > div.card_wrap.search ul:not(#filterLayer) li a"

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = "div.more_area, div.paginate a[onclick] + a"

    open fun parseDetailsThumbnail(document: Document): String? {
        val picElement = document.select("#content > div.cont_box > div.detail_body")
        val discoverPic = document.select("#content > div.cont_box > div.detail_header > span.thmb")
        return picElement.attr("style").substringAfter("url(").substringBeforeLast(")")
            .ifBlank { discoverPic.select("img").not("[alt='Representative image']").first()?.attr("src") }
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val detailElement = document.select("#content > div.cont_box > div.detail_header > div.info")
        val infoElement = document.select("#_asideDetail")

        val manga = SManga.create()
        manga.title = document.selectFirst("h1.subj, h3.subj")!!.text()
        manga.author = detailElement.select(".author:nth-of-type(1)").first()?.ownText()
            ?: detailElement.select(".author_area").first()?.ownText()
        manga.artist = detailElement.select(".author:nth-of-type(2)").first()?.ownText()
            ?: detailElement.select(".author_area").first()?.ownText() ?: manga.author
        manga.genre = detailElement.select(".genre").joinToString(", ") { it.text() }
        manga.description = infoElement.select("p.summary").text()
        manga.status = infoElement.select("p.day_info").firstOrNull()?.text().orEmpty().toStatus()
        manga.thumbnail_url = parseDetailsThumbnail(document)
        return manga
    }

    open fun String.toStatus(): Int = when {
        contains("UP") -> SManga.ONGOING
        contains("COMPLETED") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun imageUrlParse(document: Document): String = document.select("img").first()!!.attr("src")

    // Filters

    override fun getFilterList(): FilterList {
        return FilterList(
            Header("Query can not be blank"),
            Separator(),
            SearchType(getOfficialList()),
        )
    }

    override fun chapterListSelector() = "ul#_episodeList li[id*=episode]"

    private class SearchType(vals: Array<Pair<String, String>>) : UriPartFilter("Official or Challenge", vals)

    private fun getOfficialList() = arrayOf(
        Pair("Any", ""),
        Pair("Official only", "WEBTOON"),
        Pair("Challenge only", "CHALLENGE"),
    )

    open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>) :
        Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select("a")

        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(urlElement.attr("href"))
        chapter.name = element.select("a > div.row > div.info > p.sub_title > span.ellipsis").text()
        val select = element.select("a > div.row > div.num")
        if (select.isNotEmpty()) {
            chapter.name += " Ch. " + select.text().substringAfter("#")
        }
        if (element.select(".ico_bgm").isNotEmpty()) {
            chapter.name += " ♫"
        }
        chapter.date_upload = element.select("a > div.row > div.col > div.sub_info > span.date").text().let { chapterParseDate(it) } ?: 0
        return chapter
    }

    open fun chapterParseDate(date: String): Long {
        return try {
            dateFormat.parse(date)?.time ?: 0
        } catch (e: ParseException) {
            0
        }
    }

    override fun chapterListRequest(manga: SManga) = GET("https://m.webtoons.com" + manga.url, mobileHeaders)

    override fun pageListParse(document: Document): List<Page> {
        var pages = document.select("div#_imageList > img").mapIndexed { i, element -> Page(i, "", element.attr("data-url")) }

        if (pages.isNotEmpty()) { return pages }

        val docString = document.toString()

        val docUrlRegex = Regex("documentURL:.*?'(.*?)'")
        val motiontoonPathRegex = Regex("jpg:.*?'(.*?)\\{")

        val docUrl = docUrlRegex.find(docString)!!.destructured.toList()[0]
        val motiontoonPath = motiontoonPathRegex.find(docString)!!.destructured.toList()[0]
        val motiontoonResponse = client.newCall(GET(docUrl, headers)).execute()

        val motiontoonJson = json.parseToJsonElement(motiontoonResponse.body.string()).jsonObject
        val motiontoonImages = motiontoonJson["assets"]!!.jsonObject["image"]!!.jsonObject

        return motiontoonImages.entries
            .filter { it.key.contains("layer") }
            .mapIndexed { i, entry ->
                Page(i, "", motiontoonPath + entry.value.jsonPrimitive.content)
            }
    }

    companion object {
        const val URL_SEARCH_PREFIX = "url:"
    }
}
