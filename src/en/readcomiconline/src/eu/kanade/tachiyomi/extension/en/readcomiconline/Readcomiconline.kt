package eu.kanade.tachiyomi.extension.en.readcomiconline

import android.app.Application
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.lib.randomua.UserAgentType
import keiyoushi.lib.randomua.setRandomUserAgent
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.collections.mapIndexed

class Readcomiconline :
    HttpSource(),
    ConfigurableSource {

    override val name = "ReadComicOnline"

    override val baseUrl: String
        get() {
            val index = preferences.getString(MIRROR_PREF, "1")!!
                .toIntOrNull() ?: 1

            return MIRROR_URLS[index.coerceIn(MIRROR_URLS.indices)]
        }

    override val lang = "en"

    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")
        .setRandomUserAgent(
            userAgentType = UserAgentType.DESKTOP,
            filterInclude = listOf("chrome"),
        )

    override val client: OkHttpClient = network.client.newBuilder()
        .addNetworkInterceptor(::captchaInterceptor).build()

    private val context: Application by injectLazy()
    private val handler by lazy { Handler(Looper.getMainLooper()) }

    private fun captchaInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        val location = response.header("Location")
        if (location?.startsWith("/Special/AreYouHuman") == true) {
            captchaUrl = "$baseUrl/Special/AreYouHuman"
            throw Exception("Solve captcha in WebView")
        }

        return response
    }

    private var captchaUrl: String? = null

    private val preferences: SharedPreferences by getPreferencesLazy()

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/ComicList/MostPopular?page=$page", headers)

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/ComicList/LatestUpdate?page=$page", headers)

    private fun mangaFromElement(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.attr("abs:href"))
        title = element.text()
        thumbnail_url = element.selectFirst("img")!!.attr("abs:src")
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".list-comic > .item > a:first-child").map { mangaFromElement(it) }
        val hasNextPage = document.selectFirst("ul.pager > li > a:contains(Next)") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith("http")) {
            val url = query.toHttpUrlOrNull()
            val mirrorHosts = MIRROR_URLS.map { it.toHttpUrl().host }
            if (url != null && url.host in mirrorHosts &&
                url.pathSegments.size >= 2 && url.pathSegments[0] == "Comic"
            ) {
                val manga = SManga.create().apply {
                    this.url = "/Comic/${url.pathSegments[1]}"
                }
                return fetchMangaDetails(manga).map { details ->
                    details.url = manga.url
                    details.initialized = true
                    MangasPage(listOf(details), false)
                }
            }
        }
        return super.fetchSearchManga(page, query, filters)
    }

    override fun searchMangaRequest(
        page: Int,
        query: String,
        filters: FilterList,
    ): Request {
        val activeFilters = if (filters.isEmpty()) getFilterList() else filters
        val genreList = activeFilters.firstInstance<GenreList>()
        val sortOption = activeFilters.firstInstance<SortFilter>().selected
        val yearOption = activeFilters.firstInstance<YearFilter>().selected

        return if (query.isEmpty() && genreList.included.size == 1 && genreList.excluded.isEmpty() && yearOption == null) {
            // Single included genre — use /Genre/{name}/{sort} URL
            val genreName = genreList.state.first { it.isIncluded() }.name.replace(" ", "-")
            val url = baseUrl.toHttpUrl().newBuilder().apply {
                addPathSegment("Genre")
                addPathSegment(genreName)
                if (sortOption != null) addPathSegment(sortOption)
                addQueryParameter("page", page.toString())
            }.build()
            GET(url, headers)
        } else if (query.isEmpty() && genreList.included.isEmpty() && genreList.excluded.isEmpty() && yearOption == null) {
            // No query, no genres — publisher/writer/artist + sort
            val url = baseUrl.toHttpUrl().newBuilder().apply {
                var pathSegmentAdded = false

                for (filter in activeFilters) {
                    when (filter) {
                        is PublisherFilter -> {
                            if (filter.state.isNotEmpty()) {
                                addPathSegments("Publisher/${filter.state.replace(" ", "-")}")
                                pathSegmentAdded = true
                            }
                        }

                        is WriterFilter -> {
                            if (filter.state.isNotEmpty()) {
                                addPathSegments("Writer/${filter.state.replace(" ", "-")}")
                                pathSegmentAdded = true
                            }
                        }

                        is ArtistFilter -> {
                            if (filter.state.isNotEmpty()) {
                                addPathSegments("Artist/${filter.state.replace(" ", "-")}")
                                pathSegmentAdded = true
                            }
                        }

                        else -> {}
                    }

                    if (pathSegmentAdded) {
                        break
                    }
                }
                if (!pathSegmentAdded) {
                    addPathSegment("ComicList")
                    if (sortOption != null) addPathSegment(sortOption)
                } else if (sortOption != null) {
                    addPathSegment(sortOption)
                }
                addQueryParameter("page", page.toString())
            }.build()
            GET(url, headers)
        } else {
            // Has query or multiple/excluded genres — AdvanceSearch
            val url = "$baseUrl/AdvanceSearch".toHttpUrl().newBuilder().apply {
                addQueryParameter("comicName", query.trim())
                addQueryParameter("page", page.toString())
                for (filter in activeFilters) {
                    when (filter) {
                        is Status -> addQueryParameter("status", filter.selected.orEmpty())

                        is GenreList -> {
                            addQueryParameter("ig", filter.included.joinToString(","))
                            addQueryParameter("eg", filter.excluded.joinToString(","))
                        }

                        is YearFilter -> {
                            if (filter.selected != null) addQueryParameter("pubDate", filter.selected!!)
                        }

                        else -> {}
                    }
                }
            }.build()
            GET(url, headers)
        }
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    private val viewsRegex = Regex("Views:\\s*([\\d,]+)")

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val infoElement = document.selectFirst("div.barContent")!!

        val manga = SManga.create()
        manga.title = infoElement.selectFirst("a.bigChar")!!.text()
        manga.artist = infoElement.select("p:has(span:contains(Artist:)) > a").eachText().summarize()
        manga.author = infoElement.select("p:has(span:contains(Writer:)) > a").eachText().summarize()
        manga.genre = infoElement.select("p:has(span:contains(Genres:)) > a").eachText().joinToString()
        manga.description = listOfNotNull(
            infoElement.select("p:has(span:contains(Summary:)) ~ p")
                .joinToString("\n\n") { it.textPreserveLineBreaks() }
                .trim()
                .takeIf { it.isNotEmpty() },
            infoElement.select("p:has(span:contains(Publisher:))").text().takeIf { it.isNotEmpty() }?.let { "\n$it" },
            infoElement.select("p:has(span:contains(Publication date:))").text().takeIf { it.isNotEmpty() },
            viewsRegex.find(infoElement.select("p:has(span:contains(Views:))").text())
                ?.let { "Views: ${it.groupValues[1]}" },
        ).joinToString("\n")
        manga.status = infoElement.selectFirst("p:has(span:contains(Status:))")?.text().orEmpty()
            .let { parseStatus(it) }
        manga.thumbnail_url = document.selectFirst(".rightBox:eq(0) img")?.absUrl("src")
        return manga
    }

    private fun List<String>.summarize(): String = if (size > 2) "${first()} & others" else joinToString()

    private fun Element.textPreserveLineBreaks(): String {
        // Replace <br> with newline text nodes so wholeText() preserves them as line breaks.
        // .text() would collapse them into spaces.
        select("br").forEach { it.replaceWith(TextNode("\n")) }
        return wholeText()
    }

    override fun getMangaUrl(manga: SManga): String {
        val url = captchaUrl?.also { captchaUrl = null }
            ?: "$baseUrl${manga.url}"

        return url
    }

    private fun parseStatus(status: String) = when {
        status.contains("Ongoing") -> SManga.ONGOING
        status.contains("Completed") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListParse(response: Response): List<SChapter> = response.asJsoup().select("table.listing tr:gt(1)").map { element ->
        SChapter.create().apply {
            val urlElement = element.selectFirst("a")!!
            setUrlWithoutDomain(urlElement.attr("href"))
            name = urlElement.text()
            date_upload = dateFormat.tryParse(element.selectFirst("td:eq(1)")?.text())
        }
    }

    private val dateFormat = SimpleDateFormat("MM/dd/yyyy", Locale.getDefault())

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        val qualitySuffix = "&s=${serverPref()}&quality=${qualityPref()}&readType=1"
        val chapterUrl = baseUrl + chapter.url + qualitySuffix

        val chapterDoc = client.newCall(GET(chapterUrl, headers)).execute().asJsoup()
        val totalImages = chapterDoc.select("#divImage img").size

        val images = decodeImagesWv(chapterDoc, totalImages)
            .mapIndexed { i, url ->
                Page(i, imageUrl = url)
            }

        return Observable.just(images)
    }

    fun decodeImagesWv(chapterDoc: Document, totalImages: Int): List<String> {
        var innerWv: WebView? = null
        val latch = CountDownLatch(1)
        var jsonResult = ""

        handler.post {
            innerWv = WebView(context)
            innerWv.settings.loadsImagesAutomatically = false
            innerWv.settings.blockNetworkImage = true
            innerWv.settings.javaScriptEnabled = true
            innerWv.settings.userAgentString = headers["User-Agent"]

            innerWv.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    req: WebResourceRequest?,
                ): WebResourceResponse? {
                    val url = req?.url?.toString() ?: return null

                    return if (
                        url.contains(".js") ||
                        url.startsWith("data:") // initial loadDataWithBaseURL
                    ) {
                        null
                    } else {
                        WebResourceResponse("text/plain", "utf-8", null)
                    }
                }
            }

            innerWv.addJavascriptInterface(
                object {
                    @JavascriptInterface
                    fun onImagesExtracted(payload: String) {
                        jsonResult = payload
                        latch.countDown()
                    }
                },
                "AndroidBridge",
            )

            chapterDoc.select("script[defer]").remove()
            chapterDoc.body().append(
                """
                <script>
                (function() {
                    var resolved = new Set();

                    function sendFinal() {
                        var imgs = document.querySelectorAll('#divImage img');
                        for (var i = 0; i < imgs.length; i++) {
                            resolved.add(imgs[i].src);
                        }
                        AndroidBridge.onImagesExtracted(JSON.stringify(Array.from(resolved)));
                    }

                    setTimeout(function() {
                        LoadNextPages($totalImages);
                        setTimeout(sendFinal, 500);
                    }, 300);
                })();
                </script>
                """.trimIndent(),
            )

            innerWv.loadDataWithBaseURL(chapterDoc.location(), chapterDoc.outerHtml(), "text/html", "UTF-8", null)
        }

        return try {
            if (!latch.await(8, TimeUnit.SECONDS) || jsonResult.isEmpty()) {
                error("Url decoding timed out, try again")
            }
            jsonResult.parseAs<List<String>>()
        } finally {
            handler.post {
                innerWv?.stopLoading()
                innerWv?.destroy()
            }
        }
    }

    override fun pageListParse(response: Response): List<Page> = throw UnsupportedOperationException("Not used")

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    private class Status :
        SelectFilter(
            "Status",
            arrayOf(
                Pair("Any", ""),
                Pair("Completed", "Completed"),
                Pair("Ongoing", "Ongoing"),
            ),
        )
    private class Genre(name: String, val gid: String) : Filter.TriState(name)
    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Genres", genres) {
        val included: List<String>
            get() = state.filter { it.isIncluded() }.map { it.gid }

        val excluded: List<String>
            get() = state.filter { it.isExcluded() }.map { it.gid }
    }

    open class SelectFilter(displayName: String, private val options: Array<Pair<String, String>>) :
        Filter.Select<String>(
            displayName,
            options.map { it.first }.toTypedArray(),
        ) {
        open val selected get() = options[state].second.takeUnless { it.isEmpty() }
    }

    private class YearFilter :
        SelectFilter(
            "Publish Year",
            arrayOf(Pair("Any", "")) + (2026 downTo 1920).map { Pair(it.toString(), it.toString()) }.toTypedArray(),
        )

    private class PublisherFilter : Filter.Text("Publisher")
    private class WriterFilter : Filter.Text("Writer")
    private class ArtistFilter : Filter.Text("Artist")
    private class SortFilter :
        SelectFilter(
            "Sort By",
            arrayOf(
                Pair("Alphabet", ""),
                Pair("Popularity", "MostPopular"),
                Pair("Latest Update", "LatestUpdate"),
                Pair("New Comic", "Newest"),
            ),
        )

    override fun getFilterList() = FilterList(
        GenreList(getGenreList()),
        Status(),
        YearFilter(),
        Filter.Separator(),
        Filter.Header("Filters below are ignored when any of the above filters or the search is filled. (Although you can sort for a single genre)"),
        SortFilter(),
        PublisherFilter(),
        WriterFilter(),
        ArtistFilter(),
    )

    // $("select[name=\"genres\"]").map((i,el) => `Genre("${$(el).next().text().trim()}", ${i})`).get().join(',\n')
    // on https://readcomiconline.li/AdvanceSearch
    private fun getGenreList() = listOf(
        Genre("Action", "1"),
        Genre("Adventure", "2"),
        Genre("Anthology", "38"),
        Genre("Anthropomorphic", "46"),
        Genre("Biography", "41"),
        Genre("Children", "49"),
        Genre("Comedy", "3"),
        Genre("Crime", "17"),
        Genre("Drama", "19"),
        Genre("Family", "25"),
        Genre("Fantasy", "20"),
        Genre("Fighting", "31"),
        Genre("Graphic Novels", "5"),
        Genre("Historical", "28"),
        Genre("Horror", "15"),
        Genre("Leading Ladies", "35"),
        Genre("LGBTQ", "51"),
        Genre("Literature", "44"),
        Genre("Manga", "40"),
        Genre("Martial Arts", "4"),
        Genre("Mature", "8"),
        Genre("Military", "33"),
        Genre("Mini-Series", "56"),
        Genre("Movies & TV", "47"),
        Genre("Music", "55"),
        Genre("Mystery", "23"),
        Genre("Mythology", "21"),
        Genre("Personal", "48"),
        Genre("Political", "42"),
        Genre("Post-Apocalyptic", "43"),
        Genre("Psychological", "27"),
        Genre("Pulp", "39"),
        Genre("Religious", "53"),
        Genre("Robots", "9"),
        Genre("Romance", "32"),
        Genre("School Life", "52"),
        Genre("Sci-Fi", "16"),
        Genre("Slice of Life", "50"),
        Genre("Sport", "54"),
        Genre("Spy", "30"),
        Genre("Superhero", "22"),
        Genre("Supernatural", "24"),
        Genre("Suspense", "29"),
        Genre("Thriller", "18"),
        Genre("Vampires", "34"),
        Genre("Video Games", "37"),
        Genre("War", "26"),
        Genre("Western", "45"),
        Genre("Zombies", "36"),
    )
    // Preferences Code

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val mirrorPref = ListPreference(screen.context).apply {
            key = MIRROR_PREF
            title = MIRROR_PREF_TITLE
            entries = MIRROR_NAMES
            entryValues = Array(MIRROR_URLS.size) { it.toString() }
            setDefaultValue("1")
            summary = "%s"
        }

        val qualityPref = ListPreference(screen.context).apply {
            key = QUALITY_PREF
            title = QUALITY_PREF_TITLE
            entries = arrayOf("High Quality", "Low Quality")
            entryValues = arrayOf("hq", "lq")
            summary = "%s"
        }

        val serverPref = ListPreference(screen.context).apply {
            key = SERVER_PREF
            title = SERVER_PREF_TITLE
            entries = arrayOf("Server 1", "Server 2")
            entryValues = arrayOf("", "s2")
            summary = "%s"
        }

        screen.addPreference(mirrorPref)
        screen.addPreference(qualityPref)
        screen.addPreference(serverPref)
    }

    private fun qualityPref() = preferences.getString(QUALITY_PREF, "hq")

    private fun serverPref() = preferences.getString(SERVER_PREF, "")

    companion object {
        private const val QUALITY_PREF_TITLE = "Image Quality Selector"
        private const val QUALITY_PREF = "qualitypref"
        private const val SERVER_PREF_TITLE = "Server Preference"
        private const val SERVER_PREF = "serverpref"
        private const val MIRROR_PREF_TITLE = "Mirror Preference"
        private const val MIRROR_PREF = "mirrorpref"
        private val MIRROR_NAMES = arrayOf("readcomiconline.li", "rcostation.xyz")
        private val MIRROR_URLS = arrayOf("https://readcomiconline.li", "https://rcostation.xyz")
    }
}
