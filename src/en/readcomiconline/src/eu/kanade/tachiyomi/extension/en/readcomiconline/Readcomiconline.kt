package eu.kanade.tachiyomi.extension.en.readcomiconline

import android.app.Application
import android.content.SharedPreferences
import app.cash.quickjs.QuickJs
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class Readcomiconline : ConfigurableSource, ParsedHttpSource() {

    override val name = "ReadComicOnline"

    override val baseUrl = "https://readcomiconline.li"

    override val lang = "en"

    override val supportsLatest = true

    private val json: Json by injectLazy()

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .addNetworkInterceptor(::captchaInterceptor)
        .build()

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

    override fun headersBuilder() = Headers.Builder().apply {
        add("User-Agent", "Mozilla/5.0 (Windows NT 6.3; WOW64)")
    }

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override fun popularMangaSelector() = ".list-comic > .item > a:first-child"

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/ComicList/MostPopular?page=$page", headers)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/ComicList/LatestUpdate?page=$page", headers)
    }

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            setUrlWithoutDomain(element.attr("abs:href"))
            title = element.text()
            thumbnail_url = element.selectFirst("img")!!.attr("abs:src")
        }
    }

    override fun latestUpdatesFromElement(element: Element): SManga {
        return popularMangaFromElement(element)
    }

    override fun popularMangaNextPageSelector() = "ul.pager > li > a:contains(Next)"

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request { // publisher > writer > artist + sorting for both if else
        if (query.isEmpty() && (if (filters.isEmpty()) getFilterList() else filters).filterIsInstance<GenreList>().all { it.included.isEmpty() && it.excluded.isEmpty() }) {
            val url = baseUrl.toHttpUrl().newBuilder().apply {
                var pathSegmentAdded = false

                for (filter in if (filters.isEmpty()) getFilterList() else filters) {
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
                addPathSegment((if (filters.isEmpty()) getFilterList() else filters).filterIsInstance<SortFilter>().first().selected.toString())
                addQueryParameter("page", page.toString())
            }.build()
            return GET(url, headers)
        } else {
            val url = "$baseUrl/AdvanceSearch".toHttpUrl().newBuilder().apply {
                addQueryParameter("comicName", query.trim())
                addQueryParameter("page", page.toString())
                for (filter in if (filters.isEmpty()) getFilterList() else filters) {
                    when (filter) {
                        is Status -> addQueryParameter("status", arrayOf("", "Completed", "Ongoing")[filter.state])
                        is GenreList -> {
                            addQueryParameter("ig", filter.included.joinToString(","))
                            addQueryParameter("eg", filter.excluded.joinToString(","))
                        }
                        else -> {}
                    }
                }
            }.build()
            return GET(url, headers)
        }
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga {
        return popularMangaFromElement(element)
    }

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div.barContent").first()!!

        val manga = SManga.create()
        manga.artist = infoElement.select("p:has(span:contains(Artist:)) > a").first()?.text()
        manga.author = infoElement.select("p:has(span:contains(Writer:)) > a").first()?.text()
        manga.genre = infoElement.select("p:has(span:contains(Genres:)) > *:gt(0)").text()
        manga.description = infoElement.select("p:has(span:contains(Summary:)) ~ p").text()
        manga.status = infoElement.select("p:has(span:contains(Status:))").first()?.text().orEmpty().let { parseStatus(it) }
        manga.thumbnail_url = document.select(".rightBox:eq(0) img").first()?.absUrl("src")
        return manga
    }

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return client.newCall(realMangaDetailsRequest(manga))
            .asObservableSuccess()
            .map { response ->
                mangaDetailsParse(response).apply { initialized = true }
            }
    }

    private fun realMangaDetailsRequest(manga: SManga): Request =
        super.mangaDetailsRequest(manga)

    override fun mangaDetailsRequest(manga: SManga): Request =
        captchaUrl?.let { GET(it, headers) }.also { captchaUrl = null }
            ?: super.mangaDetailsRequest(manga)

    private fun parseStatus(status: String) = when {
        status.contains("Ongoing") -> SManga.ONGOING
        status.contains("Completed") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = "table.listing tr:gt(1)"

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select("a").first()!!

        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(urlElement.attr("href"))
        chapter.name = urlElement.text()
        chapter.date_upload = element.select("td:eq(1)").first()?.text()?.let {
            SimpleDateFormat("MM/dd/yyyy", Locale.getDefault()).parse(it)?.time ?: 0L
        } ?: 0
        return chapter
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val qualitySuffix = if ((qualitypref() != "lq" && serverpref() != "s2") || (qualitypref() == "lq" && serverpref() == "s2")) "&s=${serverpref()}&quality=${qualitypref()}" else "&s=${serverpref()}"
        return GET(baseUrl + chapter.url + qualitySuffix, headers)
    }

    override fun pageListParse(document: Document): List<Page> {
        if (rguardUrl == null) {
            rguardUrl = document.selectFirst("script[src*='rguard.min.js']")?.absUrl("src")
        }

        val script = document.selectFirst("script:containsData(lstImages.push)")?.data()
            ?: throw Exception("Failed to find image URLs")

        return CHAPTER_IMAGES_REGEX.findAll(script).toList()
            .let { matches -> urlDecode(matches.map { it.groupValues[1] }) }
            .mapIndexed { i, imageUrl -> Page(i, "", imageUrl) }
    }

    override fun imageUrlParse(document: Document) = ""

    private class Status : Filter.TriState("Completed")
    private class Genre(name: String, val gid: String) : Filter.TriState(name)
    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Genres", genres) {
        val included: List<String>
            get() = state.filter { it.isIncluded() }.map { it.gid }

        val excluded: List<String>
            get() = state.filter { it.isExcluded() }.map { it.gid }
    }
    open class SelectFilter(displayName: String, private val options: Array<Pair<String, String>>) : Filter.Select<String>(
        displayName,
        options.map { it.first }.toTypedArray(),
    ) {
        open val selected get() = options[state].second.takeUnless { it.isEmpty() }
    }

    private class PublisherFilter() : Filter.Text("Publisher")
    private class WriterFilter() : Filter.Text("Writer")
    private class ArtistFilter() : Filter.Text("Artist")
    private class SortFilter : SelectFilter(
        "Sort By",
        arrayOf(
            Pair("Alphabet", ""),
            Pair("Popularity", "MostPopular"),
            Pair("Latest Update", "LatestUpdate"),
            Pair("New Comic", "Newest"),
        ),
    )

    override fun getFilterList() = FilterList(
        Status(),
        GenreList(getGenreList()),
        Filter.Separator(),
        Filter.Header("Filters below is ignored when Status,Genre or the queue is not empty."),
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

    override fun setupPreferenceScreen(screen: androidx.preference.PreferenceScreen) {
        val qualitypref = androidx.preference.ListPreference(screen.context).apply {
            key = QUALITY_PREF_Title
            title = QUALITY_PREF_Title
            entries = arrayOf("High Quality", "Low Quality")
            entryValues = arrayOf("hq", "lq")
            summary = "%s"

            setOnPreferenceChangeListener { _, newValue ->
                val selected = newValue as String
                val index = this.findIndexOfValue(selected)
                val entry = entryValues[index] as String
                preferences.edit().putString(QUALITY_PREF, entry).commit()
            }
        }
        screen.addPreference(qualitypref)
        val serverpref = androidx.preference.ListPreference(screen.context).apply {
            key = SERVER_PREF_TITLE
            title = SERVER_PREF_TITLE
            entries = arrayOf("Server 1", "Server 2")
            entryValues = arrayOf("", "s2")
            summary = "%s"

            setOnPreferenceChangeListener { _, newValue ->
                val selected = newValue as String
                val index = this.findIndexOfValue(selected)
                val entry = entryValues[index] as String
                preferences.edit().putString(SERVER_PREF, entry).commit()
            }
        }
        screen.addPreference(serverpref)
    }

    private fun qualitypref() = preferences.getString(QUALITY_PREF, "hq")

    private fun serverpref() = preferences.getString(SERVER_PREF, "")

    private var rguardUrl: String? = null

    private val rguardBytecode: ByteArray by lazy {
        val cacheDays = if (rguardUrl == null) 1 else 7
        val cacheControl = CacheControl.Builder()
            .maxAge(cacheDays, TimeUnit.DAYS)
            .build()

        val scriptUrl = rguardUrl ?: "$baseUrl/Scripts/rguard.min.js"
        val scriptRequest = GET(scriptUrl, headers, cache = cacheControl)
        val scriptResponse = client.newCall(scriptRequest).execute()
        val scriptBody = scriptResponse.body.string()

        QuickJs.create().use {
            it.compile(DISABLE_JS_SCRIPT + scriptBody + ATOB_SCRIPT, "?")
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun urlDecode(urls: List<String>): List<String> {
        return QuickJs.create().use {
            it.execute(rguardBytecode)

            val script = """
                var images = ${json.encodeToJsonElement(urls)};
                beau(images);
                images;
            """.trimIndent()
            (it.evaluate(script) as Array<Any>).map { it as String }.toList()
        }
    }

    companion object {
        private const val QUALITY_PREF_Title = "Image Quality Selector"
        private const val QUALITY_PREF = "qualitypref"
        private const val SERVER_PREF_TITLE = "Server Preference"
        private const val SERVER_PREF = "serverpref"

        private val CHAPTER_IMAGES_REGEX = "lstImages\\.push\\([\"'](.*)[\"']\\)".toRegex()

        private val DISABLE_JS_SCRIPT = """
            const handler = {
                get: function(target, _) {
                    return function() {
                        return target;
                    };
                },
                apply: function(_, __, ___) {
                    return new Proxy({}, handler);
                }
            };

            document = new Proxy({}, handler);
            window = new Proxy({}, handler);
            console = new Proxy({}, handler);
            ${'$'} = new Proxy(function() {}, handler);
        """.trimIndent()

        /*
         * The MIT License (MIT)
         * Copyright (c) 2014 MaxArt2501
         */
        private val ATOB_SCRIPT = """
            var b64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=",
                b64re = /^(?:[A-Za-z\d+\/]{4})*?(?:[A-Za-z\d+\/]{2}(?:==)?|[A-Za-z\d+\/]{3}=?)?$/;

            atob = function(string) {
                // atob can work with strings with whitespaces, even inside the encoded part,
                // but only \t, \n, \f, \r and ' ', which can be stripped.
                string = String(string).replace(/[\t\n\f\r ]+/g, "");
                if (!b64re.test(string))
                    throw new TypeError("Failed to execute 'atob' on 'Window': The string to be decoded is not correctly encoded.");

                // Adding the padding if missing, for semplicity
                string += "==".slice(2 - (string.length & 3));
                var bitmap, result = "", r1, r2, i = 0;
                for (; i < string.length;) {
                    bitmap = b64.indexOf(string.charAt(i++)) << 18 | b64.indexOf(string.charAt(i++)) << 12
                            | (r1 = b64.indexOf(string.charAt(i++))) << 6 | (r2 = b64.indexOf(string.charAt(i++)));

                    result += r1 === 64 ? String.fromCharCode(bitmap >> 16 & 255)
                            : r2 === 64 ? String.fromCharCode(bitmap >> 16 & 255, bitmap >> 8 & 255)
                            : String.fromCharCode(bitmap >> 16 & 255, bitmap >> 8 & 255, bitmap & 255);
                }
                return result;
            };
        """.trimIndent()
    }
}
