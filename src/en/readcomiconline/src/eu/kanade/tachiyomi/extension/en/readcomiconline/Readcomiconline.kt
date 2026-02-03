package eu.kanade.tachiyomi.extension.en.readcomiconline

import android.content.SharedPreferences
import app.cash.quickjs.QuickJs
import eu.kanade.tachiyomi.lib.randomua.UserAgentType
import eu.kanade.tachiyomi.lib.randomua.setRandomUserAgent
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class Readcomiconline :
    ParsedHttpSource(),
    ConfigurableSource {

    override val name = "ReadComicOnline"

    override val baseUrl = "https://readcomiconline.li"

    override val lang = "en"

    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder().set("Referer", "$baseUrl/")

    override val client: OkHttpClient = network.cloudflareClient.newBuilder().setRandomUserAgent(
        userAgentType = UserAgentType.DESKTOP,
        filterInclude = listOf("chrome"),
    ).addNetworkInterceptor(::captchaInterceptor).build()

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

    override fun popularMangaSelector() = ".list-comic > .item > a:first-child"

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/ComicList/MostPopular?page=$page", headers)

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/ComicList/LatestUpdate?page=$page", headers)

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.attr("abs:href"))
        title = element.text()
        thumbnail_url = element.selectFirst("img")!!.attr("abs:src")
    }

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun popularMangaNextPageSelector() = "ul.pager > li > a:contains(Next)"

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaRequest(
        page: Int,
        query: String,
        filters: FilterList,
    ): Request { // publisher > writer > artist + sorting for both if else
        if (query.isEmpty() && (if (filters.isEmpty()) getFilterList() else filters).filterIsInstance<GenreList>()
                .all { it.included.isEmpty() && it.excluded.isEmpty() }
        ) {
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
                addPathSegment(
                    (if (filters.isEmpty()) getFilterList() else filters).filterIsInstance<SortFilter>()
                        .first().selected.toString(),
                )
                addQueryParameter("page", page.toString())
            }.build()
            return GET(url, headers)
        } else {
            val url = "$baseUrl/AdvanceSearch".toHttpUrl().newBuilder().apply {
                addQueryParameter("comicName", query.trim())
                addQueryParameter("page", page.toString())
                for (filter in if (filters.isEmpty()) getFilterList() else filters) {
                    when (filter) {
                        is Status -> addQueryParameter(
                            "status",
                            arrayOf("", "Completed", "Ongoing")[filter.state],
                        )

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

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div.barContent").first()!!

        val manga = SManga.create()
        manga.title = infoElement.selectFirst("a.bigChar")!!.text()
        manga.artist = infoElement.select("p:has(span:contains(Artist:)) > a").first()?.text()
        manga.author = infoElement.select("p:has(span:contains(Writer:)) > a").first()?.text()
        manga.genre = infoElement.select("p:has(span:contains(Genres:)) > *:gt(0)").text()
        manga.description = infoElement.select("p:has(span:contains(Summary:)) ~ p").text()
        manga.status = infoElement.select("p:has(span:contains(Status:))").first()?.text().orEmpty()
            .let { parseStatus(it) }
        manga.thumbnail_url = document.select(".rightBox:eq(0) img").first()?.absUrl("src")
        return manga
    }

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = client.newCall(realMangaDetailsRequest(manga)).asObservableSuccess()
        .map { response ->
            mangaDetailsParse(response).apply { initialized = true }
        }

    private fun realMangaDetailsRequest(manga: SManga): Request = super.mangaDetailsRequest(manga)

    override fun mangaDetailsRequest(manga: SManga): Request = captchaUrl?.let { GET(it, headers) }.also { captchaUrl = null }
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
        chapter.date_upload = dateFormat.tryParse(element.selectFirst("td:eq(1)")?.text())
        return chapter
    }

    private val dateFormat = SimpleDateFormat("MM/dd/yyyy", Locale.getDefault())

    override fun pageListRequest(chapter: SChapter): Request {
        val qualitySuffix =
            if ((qualityPref() != "lq" && serverPref() != "s2") || (qualityPref() == "lq" && serverPref() == "s2")) {
                "&s=${serverPref()}&quality=${qualityPref()}&readType=1"
            } else {
                "&s=${serverPref()}&readType=1"
            }

        return GET(baseUrl + chapter.url + qualitySuffix, headers)
    }

    override fun pageListParse(document: Document): List<Page> {
        // Declare some important values first
        var encryptedLinks = mutableListOf<String>()
        val useSecondServer = serverPref() == "s2"

        // Get script elements
        val scripts = document.select("script")

        // We'll evaluate every script that exists in the HTML
        if (remoteConfigItem == null) {
            throw IOException("Failed to retrieve configuration")
        }

        for (script in scripts) {
            QuickJs.create().use {
                val eval =
                    "let _encryptedString = ${Json.encodeToString(script.data().trimIndent())};let _useServer2 = $useSecondServer;${remoteConfigItem!!.imageDecryptEval}"
                val evalResult = (it.evaluate(eval) as String).parseAs<List<String>>()

                // Add results to 'encryptedLinks'
                encryptedLinks.addAll(evalResult)
            }
        }

        encryptedLinks = encryptedLinks.let { links ->
            if (remoteConfigItem!!.postDecryptEval != null) {
                QuickJs.create().use {
                    val eval = "let _decryptedLinks = ${Json.encodeToString(links)};let _useServer2 = $useSecondServer;${remoteConfigItem!!.postDecryptEval}"
                    (it.evaluate(eval) as String).parseAs<MutableList<String>>()
                }
            } else {
                links
            }
        }

        return encryptedLinks.mapIndexedNotNull { idx, url ->
            if (!remoteConfigItem!!.shouldVerifyLinks) {
                Page(idx, imageUrl = url)
            } else {
                val request = Request.Builder().url(url).head().build()

                client.newCall(request).execute().use {
                    if (it.isSuccessful) {
                        Page(idx, imageUrl = url)
                    } else {
                        null // Remove from list
                    }
                }
            }
        }
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

    open class SelectFilter(displayName: String, private val options: Array<Pair<String, String>>) :
        Filter.Select<String>(
            displayName,
            options.map { it.first }.toTypedArray(),
        ) {
        open val selected get() = options[state].second.takeUnless { it.isEmpty() }
    }

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
        val remoteConfigPref = androidx.preference.EditTextPreference(screen.context).apply {
            key = IMAGE_REMOTE_CONFIG_PREF
            title = IMAGE_REMOTE_CONFIG_TITLE
            summary = IMAGE_REMOTE_CONFIG_SUMMARY
            setDefaultValue(IMAGE_REMOTE_CONFIG_DEFAULT)

            setOnPreferenceChangeListener { _, newValue ->
                val commitResult =
                    preferences.edit().putString(IMAGE_REMOTE_CONFIG_PREF, newValue as String)
                        .commit()

                if (commitResult) {
                    // Make it null so remoteConfigItem would request for a link again
                    remoteConfigItem = null
                }

                commitResult
            }
        }

        val qualityPref = androidx.preference.ListPreference(screen.context).apply {
            key = QUALITY_PREF
            title = QUALITY_PREF_TITLE
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

        val serverPref = androidx.preference.ListPreference(screen.context).apply {
            key = SERVER_PREF
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

        screen.addPreference(remoteConfigPref)
        screen.addPreference(qualityPref)
        screen.addPreference(serverPref)
    }

    private fun qualityPref() = preferences.getString(QUALITY_PREF, "hq")

    private fun serverPref() = preferences.getString(SERVER_PREF, "")

    private var remoteConfigItem: RemoteConfigDTO? = null
        get() {
            if (field != null) {
                return field
            }

            val configLink = preferences.getString(
                IMAGE_REMOTE_CONFIG_PREF,
                IMAGE_REMOTE_CONFIG_DEFAULT,
            )?.addBustQuery() ?: return null

            try {
                val configResponse = client.newCall(GET(configLink)).execute()

                field = configResponse.parseAs<RemoteConfigDTO>()
                configResponse.close()
                return field
            } catch (_: IOException) {
                return null
            }
        }

    private fun String.addBustQuery(): String = "$this?bust=${Calendar.getInstance().time.time}"

    @Serializable
    private class RemoteConfigDTO(
        val imageDecryptEval: String,
        val postDecryptEval: String?,
        val shouldVerifyLinks: Boolean,
    )

    companion object {
        private const val QUALITY_PREF_TITLE = "Image Quality Selector"
        private const val QUALITY_PREF = "qualitypref"
        private const val SERVER_PREF_TITLE = "Server Preference"
        private const val SERVER_PREF = "serverpref"
        private const val IMAGE_REMOTE_CONFIG_TITLE = "Remote Config"
        private const val IMAGE_REMOTE_CONFIG_SUMMARY = "Remote Config Link"
        private const val IMAGE_REMOTE_CONFIG_PREF = "imageuseremotelinkpref"
        private const val IMAGE_REMOTE_CONFIG_DEFAULT =
            "https://raw.githubusercontent.com/keiyoushi/extensions-source/refs/heads/main/src/en/readcomiconline/config.json"
    }
}
