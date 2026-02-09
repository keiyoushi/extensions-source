package eu.kanade.tachiyomi.extension.fr.japscan

import android.app.Application
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.collections.mapIndexed

class Japscan :
    ParsedHttpSource(),
    ConfigurableSource {

    override val id: Long = 11

    override val name = "Japscan"

    // Sometimes an adblock blocker will pop up, preventing the user from opening
    // a cloudflare protected page
    private val internalBaseUrl = "https://www.japscan.foo"
    override val baseUrl = "$internalBaseUrl/mangas/?sort=popular&p=1"

    override val lang = "fr"

    override val supportsLatest = true

    private val json: Json by injectLazy()

    private val preferences: SharedPreferences by getPreferencesLazy()

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(1, 2)
        .build()

    companion object {
        val dateFormat by lazy {
            SimpleDateFormat("dd MMM yyyy", Locale.US)
        }
        private const val SHOW_SPOILER_CHAPTERS_TITLE = "Les chapitres en Anglais ou non traduit sont upload en tant que \" Spoilers \" sur Japscan"
        private const val SHOW_SPOILER_CHAPTERS = "JAPSCAN_SPOILER_CHAPTERS"
        private val prefsEntries = arrayOf("Montrer uniquement les chapitres traduit en Français", "Montrer les chapitres spoiler")
        private val prefsEntryValues = arrayOf("hide", "show")
    }

    private fun chapterListPref() = preferences.getString(SHOW_SPOILER_CHAPTERS, "hide")

    override fun headersBuilder() = super.headersBuilder()
        .add("referer", "$internalBaseUrl/")

    // Popular
    override fun popularMangaRequest(page: Int): Request = GET("$internalBaseUrl/mangas/?sort=popular&p=$page", headers)

    override fun popularMangaNextPageSelector() = ".pagination > li:last-child:not(.disabled)"

    override fun popularMangaSelector() = ".mangas-list .manga-block:not(:has(a[href='']))"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("a").first()!!.let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.text()
            manga.thumbnail_url = it.selectFirst("img")?.attr("abs:data-src")
        }
        return manga
    }

    // Latest
    override fun latestUpdatesRequest(page: Int): Request = GET("$internalBaseUrl/mangas/?sort=updated&p=$page", headers)

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector(): String = popularMangaNextPageSelector()

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isEmpty()) {
            val url = internalBaseUrl.toHttpUrl().newBuilder().apply {
                addPathSegment("mangas")

                filters.forEach { filter ->
                    when (filter) {
                        is TextField -> addPathSegment(((page - 1) + filter.state.toInt()).toString())
                        is PageList -> addPathSegment(((page - 1) + filter.values[filter.state]).toString())
                        else -> {}
                    }
                }
            }.build()

            return GET(url, headers)
        } else {
            val formBody = FormBody.Builder()
                .add("search", query)
                .build()
            val searchHeaders = headers.newBuilder()
                .add("X-Requested-With", "XMLHttpRequest")
                .build()

            return POST("$internalBaseUrl/ls/", searchHeaders, formBody)
        }
    }

    override fun searchMangaNextPageSelector(): String = popularMangaSelector()

    override fun searchMangaSelector(): String = "div.card div.p-2"

    override fun searchMangaParse(response: Response): MangasPage {
        if (response.request.url.pathSegments.first() == "ls") {
            val jsonResult = json.parseToJsonElement(response.body.string()).jsonArray

            val mangaList = jsonResult.map { jsonEl -> searchMangaFromJson(jsonEl.jsonObject) }

            return MangasPage(mangaList, hasNextPage = false)
        }

        val baseUrlHost = internalBaseUrl.toHttpUrl().host
        val document = response.asJsoup()
        val manga = document
            .select(searchMangaSelector())
            .filter { it ->
                // Filter out ads masquerading as search results
                it.select("p a").attr("abs:href").toHttpUrl().host == baseUrlHost
            }
            .map(::searchMangaFromElement)
        val hasNextPage = document.selectFirst(searchMangaNextPageSelector()) != null

        return MangasPage(manga, hasNextPage)
    }

    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        thumbnail_url = element.select("img").attr("abs:src")
        element.select("p a").let {
            title = it.text()
            url = it.attr("href")
        }
    }

    private fun searchMangaFromJson(jsonObj: JsonObject): SManga = SManga.create().apply {
        url = jsonObj["url"]!!.jsonPrimitive.content
        title = jsonObj["name"]!!.jsonPrimitive.content
        thumbnail_url = internalBaseUrl + jsonObj["image"]!!.jsonPrimitive.content
    }

    override fun mangaDetailsRequest(manga: SManga): Request = GET(internalBaseUrl + manga.url, headers)

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.selectFirst("#main .card-body")!!
        val manga = SManga.create()

        manga.thumbnail_url = infoElement.selectFirst("img")?.attr("abs:src")

        val infoRows = infoElement.select(".row, .d-flex")
        infoRows.select("p").forEach { el ->
            when (el.select("span").text().trim()) {
                "Auteur(s):" -> manga.author = el.text().replace("Auteur(s):", "").trim()

                "Artiste(s):" -> manga.artist = el.text().replace("Artiste(s):", "").trim()

                "Genre(s):" -> manga.genre = el.text().replace("Genre(s):", "").trim()

                "Statut:" -> manga.status = el.text().replace("Statut:", "").trim().let {
                    parseStatus(it)
                }
            }
        }
        manga.description = infoElement.selectFirst("div:contains(Synopsis) + p")?.ownText().orEmpty()

        return manga
    }

    private fun parseStatus(status: String) = when {
        status.contains("En Cours") -> SManga.ONGOING
        status.contains("Terminé") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun getChapterUrl(chapter: SChapter): String = internalBaseUrl + chapter.url

    override fun chapterListRequest(manga: SManga): Request = GET(internalBaseUrl + manga.url, headers)

    override fun chapterListSelector() = "#list_chapters > div.collapse > div.list_chapters" +
        if (chapterListPref() == "hide") {
            ":not(:has(.badge:contains(SPOILER),.badge:contains(RAW),.badge:contains(VUS)))"
        } else {
            ""
        }
    // JapScan sometimes uploads some "spoiler preview" chapters, containing 2 or 3 untranslated pictures taken from a raw. Sometimes they also upload full RAWs/US versions and replace them with a translation as soon as available.
    // Those have a span.badge "SPOILER" or "RAW". The additional pseudo selector makes sure to exclude these from the chapter list.

    override fun chapterFromElement(element: Element): SChapter {
        // Only search for a tag with any attribute containing manga/manhua/manhwa
        val urlPairs = element.getElementsByTag("a")
            .mapNotNull { el ->
                // Find the first attribute whose value matches the chapter URL pattern
                val attrMatch = el.attributes().asList().firstOrNull { attr ->
                    val value = attr.value
                    value.startsWith("/manga/") || value.startsWith("/manhua/") || value.startsWith("/manhwa/") || value.startsWith("/bd/") || value.startsWith("/comic/")
                }
                if (attrMatch != null) {
                    val name = el.ownText().ifBlank { el.text() }
                    // Mark if the attribute is not "href"
                    val isNonHref = attrMatch.key != "href"
                    Triple(name, attrMatch.value, isNonHref)
                } else {
                    null
                }
            }
            .distinctBy { it.second }
            .sortedWith(
                compareByDescending<Triple<String, String, Boolean>> { it.third }
                    .thenBy { it.second.length },
            ) // Prefer non-href first, then shorter URLs
            .map { Pair(it.first, it.second) }

        var foundPair: Pair<String, String>? = urlPairs.firstOrNull()
        // var log = urlPairs.size.toString() + " URLs found:\n"
        // for ((name, url) in urlPairs) {
        //     val testUrl = internalBaseUrl + url
        //     val response = client.newCall(GET(testUrl, headers)).execute()
        //     log += "$name: $testUrl => ${response}\n"
        //     if (response.isSuccessful) {
        //         foundPair = Pair(name, url)
        //         response.close()
        //         break
        //     }
        //     response.close()
        // }
        if (foundPair == null) {
            throw Exception("Impossible de trouver l'URL du chapitre")
        }

        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(foundPair.second)
        chapter.name = foundPair.first
        chapter.date_upload = element.selectFirst("span")?.text()?.trim()?.let { parseChapterDate(it) } ?: 0L
        return chapter
    }

    private fun parseChapterDate(date: String) = runCatching {
        dateFormat.parse(date)!!.time
    }.getOrDefault(0L)

    @Serializable
    class ChapterDetails(
        val imagesLink: List<String>,
    )

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        try {
            val document = client.newCall(GET("$internalBaseUrl${chapter.url}")).execute().asJsoup()
            // Get only usable crypted b64
            val atad = document.select("i[data-atad]").attr("data-atad").substring(7)
            val mapping =
                "M7HXtiwLKdpIBkEbQ2OaF8Sxmz1yGReU4q5DncgsT6jVA3Pfv0WuJ9YCZNhlor".reversed()
            val reference =
                "uGJ657yOSbZRtplgHEYPBwCqaxQIizDWmTLMsAeNocnX0d98rf4Kj1kvh3UFV2".reversed()
            val decrypted =
                atad.replace(Regex("[A-Z0-9]", RegexOption.IGNORE_CASE)) { matchResult ->
                    val char = matchResult.value[0]
                    val index = reference.indexOf(char)
                    (if (index != -1) mapping[index] else char).toString()
                }
            val fromB64 = String(Base64.decode(decrypted, Base64.DEFAULT)).parseAs<ChapterDetails>()
            if (fromB64.imagesLink.isEmpty()) throw UnsupportedOperationException("Can't parse Images")
            return Observable.just(
                fromB64.imagesLink.mapIndexed { i, url ->
                    Page(i, imageUrl = "$url?o=1")
                },
            )
        } catch (e: Exception) {
            return fallbackFetchPageList(chapter)
        }
    }

    fun fallbackFetchPageList(chapter: SChapter): Observable<List<Page>> {
        val interfaceName = randomString()

        val handler = Handler(Looper.getMainLooper())
        val latch = CountDownLatch(1)
        val jsInterface = JsInterface(latch)
        var webView: WebView? = null

        handler.post {
            val innerWv = WebView(Injekt.get<Application>())

            webView = innerWv
            innerWv.settings.domStorageEnabled = true
            innerWv.settings.javaScriptEnabled = true
            innerWv.settings.blockNetworkImage = true
            innerWv.settings.userAgentString = headers["User-Agent"]
            innerWv.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            innerWv.addJavascriptInterface(jsInterface, interfaceName)

            innerWv.webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    view?.evaluateJavascript(
                        """
                            Object.defineProperty(Object.prototype, 'imagesLink', {
                                set: function(value) {
                                    window.$interfaceName.passPayload(JSON.stringify(value));
                                    Object.defineProperty(this, '_imagesLink', {
                                        value: value,
                                        writable: true,
                                        enumerable: false,
                                        configurable: true
                                    });
                                },
                                get: function() {
                                    return this._imagesLink;
                                },
                                enumerable: false,
                                configurable: true
                            });
                        """.trimIndent(),
                    ) {}
                }
            }

            innerWv.loadUrl(
                "$internalBaseUrl${chapter.url}",
                headers.toMap(),
            )
        }

        latch.await(10, TimeUnit.SECONDS)
        handler.post { webView?.destroy() }

        if (latch.count == 1L) {
            throw Exception("Erreur lors de la récupération des pages")
        }

        val baseUrlHost = internalBaseUrl.toHttpUrl().host.substringAfter("www.")
        val images = jsInterface
            .images
            .filter { it.toHttpUrl().host.endsWith(baseUrlHost) } // Pages not served through their CDN are probably ads
            .mapIndexed { i, url ->
                Page(i, imageUrl = url)
            }

        return Observable.just(images)
    }

    override fun pageListParse(document: Document) = throw Exception("Not used")

    override fun imageUrlParse(document: Document): String = ""

    // Filters
    private class TextField(name: String) : Filter.Text(name)

    private class PageList(pages: Array<Int>) : Filter.Select<Int>("Page #", arrayOf(0, *pages))

    // Prefs
    override fun setupPreferenceScreen(screen: androidx.preference.PreferenceScreen) {
        val chapterListPref = androidx.preference.ListPreference(screen.context).apply {
            key = SHOW_SPOILER_CHAPTERS_TITLE
            title = SHOW_SPOILER_CHAPTERS_TITLE
            entries = prefsEntries
            entryValues = prefsEntryValues
            summary = "%s"

            setOnPreferenceChangeListener { _, newValue ->
                val selected = newValue as String
                val index = this.findIndexOfValue(selected)
                val entry = entryValues[index] as String
                preferences.edit().putString(SHOW_SPOILER_CHAPTERS, entry).commit()
            }
        }
        screen.addPreference(chapterListPref)
    }

    private fun randomString(length: Int = 10): String {
        val charPool = ('a'..'z') + ('A'..'Z')
        return List(length) { charPool.random() }.joinToString("")
    }

    internal class JsInterface(private val latch: CountDownLatch) {
        var images: List<String> = listOf()
            private set

        @JavascriptInterface
        @Suppress("UNUSED")
        fun passPayload(rawData: String) {
            try {
                images = rawData.parseAs<List<String>>()
                    .map { "$it?y=1" }
                latch.countDown()
            } catch (_: Exception) {
                return
            }
        }
    }
}
