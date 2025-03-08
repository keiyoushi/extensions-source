package eu.kanade.tachiyomi.extension.fr.japscan

import android.annotation.SuppressLint
import android.app.Application
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
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
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class Japscan : ConfigurableSource, ParsedHttpSource() {

    override val id: Long = 11

    override val name = "Japscan"

    override val baseUrl = "https://www.japscan.lol"

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
        private const val SHOW_SPOILER_CHAPTERS_Title = "Les chapitres en Anglais ou non traduit sont upload en tant que \" Spoilers \" sur Japscan"
        private const val SHOW_SPOILER_CHAPTERS = "JAPSCAN_SPOILER_CHAPTERS"
        private val prefsEntries = arrayOf("Montrer uniquement les chapitres traduit en Français", "Montrer les chapitres spoiler")
        private val prefsEntryValues = arrayOf("hide", "show")
    }

    private fun chapterListPref() = preferences.getString(SHOW_SPOILER_CHAPTERS, "hide")

    override fun headersBuilder() = super.headersBuilder()
        .add("referer", "$baseUrl/")

    // Popular
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/mangas/?sort=popular&p=$page", headers)
    }

    override fun popularMangaNextPageSelector() = ".pagination > li:last-child:not(.disabled)"

    override fun popularMangaSelector() = ".mangas-list .manga-block:not(:has(a[href='']))"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("a").first()!!.let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.text()
            manga.thumbnail_url = "$baseUrl/imgs/${it.attr("href").replace(Regex("/$"),".jpg").replace("manga","mangas")}".lowercase(Locale.ROOT)
        }
        return manga
    }

    // Latest
    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/mangas/?sort=updated&p=$page", headers)
    }

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector(): String = popularMangaNextPageSelector()

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isEmpty()) {
            val url = baseUrl.toHttpUrl().newBuilder().apply {
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

            return POST("$baseUrl/live-search/", searchHeaders, formBody)
        }
    }

    override fun searchMangaNextPageSelector(): String = popularMangaSelector()

    override fun searchMangaSelector(): String = "div.card div.p-2"

    override fun searchMangaParse(response: Response): MangasPage {
        if (response.request.url.pathSegments.first() == "live-search") {
            val jsonResult = json.parseToJsonElement(response.body.string()).jsonArray

            val mangaList = jsonResult.map { jsonEl -> searchMangaFromJson(jsonEl.jsonObject) }

            return MangasPage(mangaList, hasNextPage = false)
        }

        val baseUrlHost = baseUrl.toHttpUrl().host
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
        thumbnail_url = baseUrl + jsonObj["image"]!!.jsonPrimitive.content
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.selectFirst("#main .card-body")!!

        val manga = SManga.create()
        val path = document.location().replaceFirst("$baseUrl/", "")
        manga.thumbnail_url = "$baseUrl/imgs/${path.replace(Regex("/$"),".jpg").replace("manga","mangas")}".lowercase(Locale.ROOT)

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
        manga.description = infoElement.select("div:contains(Synopsis) + p").text().orEmpty()

        return manga
    }

    private fun parseStatus(status: String) = when {
        status.contains("En Cours") -> SManga.ONGOING
        status.contains("Terminé") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = "#chapters_list > div.collapse > div.chapters_list" +
        if (chapterListPref() == "hide") { ":not(:has(.badge:contains(SPOILER),.badge:contains(RAW),.badge:contains(VUS)))" } else { "" }
    // JapScan sometimes uploads some "spoiler preview" chapters, containing 2 or 3 untranslated pictures taken from a raw. Sometimes they also upload full RAWs/US versions and replace them with a translation as soon as available.
    // Those have a span.badge "SPOILER" or "RAW". The additional pseudo selector makes sure to exclude these from the chapter list.

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.selectFirst("a")!!

        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(urlElement.attr("href"))
        chapter.name = urlElement.ownText()
        // Using ownText() doesn't include childs' text, like "VUS" or "RAW" badges, in the chapter name.
        chapter.date_upload = element.selectFirst("span")!!.text().trim().let { parseChapterDate(it) }
        return chapter
    }

    private fun parseChapterDate(date: String) = runCatching {
        dateFormat.parse(date)!!.time
    }.getOrDefault(0L)

    @SuppressLint("SetJavaScriptEnabled")
    override fun pageListParse(document: Document): List<Page> {
        val interfaceName = randomString()
        document.body().prepend(
            """
            <script>
                const _atob = atob;
                atob = function(arg) {
                    let data = _atob(arg)
                    window.$interfaceName.passPayload(data);
                    return data;
                };
            </script>
            """.trimIndent(),
        )

        val handler = Handler(Looper.getMainLooper())
        val latch = CountDownLatch(1)
        val jsInterface = JsInterface(latch)
        var webView: WebView? = null

        handler.post {
            val innerWv = WebView(Injekt.get<Application>())

            webView = innerWv
            innerWv.settings.javaScriptEnabled = true
            innerWv.settings.blockNetworkImage = true
            innerWv.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            innerWv.addJavascriptInterface(jsInterface, interfaceName)

            innerWv.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    url: String?,
                ): Boolean {
                    url ?: return true
                    return !url.contains("/zjs/")
                }
            }

            innerWv.loadDataWithBaseURL(
                document.location(),
                document.outerHtml(),
                "text/html",
                "UTF-8",
                null,
            )
        }

        latch.await(10, TimeUnit.SECONDS)
        handler.post { webView?.destroy() }

        if (latch.count == 1L) {
            throw Exception("Timed out decrypting image links")
        }

        val baseUrlHost = baseUrl.toHttpUrl().host

        return jsInterface
            .images
            .filterNot { it.toHttpUrl().host == baseUrlHost } // Pages not served through their CDN are probably ads
            .mapIndexed { i, url ->
                Page(i, imageUrl = url)
            }
    }

    override fun imageUrlParse(document: Document): String = ""

    // Filters
    private class TextField(name: String) : Filter.Text(name)

    private class PageList(pages: Array<Int>) : Filter.Select<Int>("Page #", arrayOf(0, *pages))

    // Prefs
    override fun setupPreferenceScreen(screen: androidx.preference.PreferenceScreen) {
        val chapterListPref = androidx.preference.ListPreference(screen.context).apply {
            key = SHOW_SPOILER_CHAPTERS_Title
            title = SHOW_SPOILER_CHAPTERS_Title
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
        private val json: Json by injectLazy()

        var images: List<String> = listOf()
            private set

        @JavascriptInterface
        @Suppress("UNUSED")
        fun passPayload(rawData: String) {
            try {
                val data = json.parseToJsonElement(rawData).jsonObject

                images = data["imagesLink"]!!.jsonArray.map { it.jsonPrimitive.content }
                latch.countDown()
            } catch (_: Exception) {
                return
            }
        }
    }
}
