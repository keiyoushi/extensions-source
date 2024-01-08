package eu.kanade.tachiyomi.extension.en.madokami

import android.app.Application
import android.content.SharedPreferences
import android.text.InputType
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.io.IOException
import java.net.URLDecoder
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class Madokami : ConfigurableSource, ParsedHttpSource() {
    override val name = "Madokami"
    override val baseUrl = "https://manga.madokami.al"
    override val lang = "en"
    override val supportsLatest = false

    private val json: Json by injectLazy()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH)

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    private fun authenticate(request: Request): Request {
        val credential = Credentials.basic(preferences.getString("username", "")!!, preferences.getString("password", "")!!)
        return request.newBuilder().header("Authorization", credential).build()
    }

    override val client: OkHttpClient = super.client.newBuilder().addInterceptor { chain ->
        val response = chain.proceed(chain.request())
        if (response.code == 401) throw IOException("You are currently logged out.\nGo to Extensions > Details to input your credentials.")
        response
    }.build()

    override fun latestUpdatesSelector() = ""
    override fun latestUpdatesFromElement(element: Element): SManga = throw Exception("Unsupported!")
    override fun latestUpdatesNextPageSelector(): String? = null
    override fun latestUpdatesRequest(page: Int) = throw Exception("Unsupported!")

    override fun popularMangaSelector(): String = "table.mobile-files-table tbody tr td:nth-child(1) a:nth-child(1)"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.url = element.attr("href")
        manga.title = URLDecoder.decode(element.attr("href").split("/").last(), "UTF-8").trimStart('!')
        return manga
    }

    override fun popularMangaNextPageSelector(): String? = null

    override fun popularMangaRequest(page: Int): Request = authenticate(GET("$baseUrl/recent", headers))

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = authenticate(GET("$baseUrl/search?q=$query", headers))

    override fun searchMangaSelector() = "div.container table tbody tr td:nth-child(1) a:nth-child(1)"

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector(): String? = null

    override fun mangaDetailsRequest(manga: SManga): Request {
        val url = (baseUrl + manga.url).toHttpUrlOrNull()!!
        if (url.pathSize > 5 && url.pathSegments[0] == "Manga" && url.pathSegments[1].length == 1) {
            val builder = url.newBuilder()
            for (i in 5 until url.pathSize) { builder.removePathSegment(5) }
            return authenticate(GET(builder.build().toUrl().toExternalForm(), headers))
        }
        if (url.pathSize > 2 && url.pathSegments[0] == "Raws") {
            val builder = url.newBuilder()
            // to accomodate path pattern of /Raws/Magz/Series, this will remove all latter path segments that starts with !
            // will fails if there's ever manga with ! prefix, but for now it works
            var i = url.pathSize - 1
            while (url.pathSegments[i].startsWith("!") && i >= 2) { builder.removePathSegment(i); i--; }
            return authenticate(GET(builder.build().toUrl().toExternalForm(), headers))
        }
        return authenticate(GET(url.toUrl().toExternalForm(), headers))
    }

    /**
     * Returns the details of the manga from the given [document].
     *
     * @param document the parsed document.
     */
    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        manga.author = document.select("a[itemprop=\"author\"]").joinToString(", ") { it.text() }
        manga.description = "Tags: " + document.select("div.genres[itemprop=\"keywords\"] a.tag.tag-category").joinToString(", ") { it.text() }
        manga.genre = document.select("div.genres a.tag[itemprop=\"genre\"]").joinToString(", ") { it.text() }
        manga.status = if (document.select("span.scanstatus").text() == "Yes") SManga.COMPLETED else SManga.UNKNOWN
        manga.thumbnail_url = document.select("div.manga-info img[itemprop=\"image\"]").attr("src")
        return manga
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/" + manga.url.trimStart('/')

    override fun chapterListRequest(manga: SManga) = authenticate(GET("$baseUrl/" + manga.url, headers))

    override fun chapterListParse(response: Response): List<SChapter> = super.chapterListParse(response).reversed()

    /**
     * Returns the Jsoup selector that returns a list of [Element] corresponding to each chapter.
     */
    override fun chapterListSelector() = "table#index-table > tbody > tr > td:nth-child(6) > a"

    /**
     * Returns a chapter from the given element.
     *
     * @param element an element obtained from [chapterListSelector].
     */
    override fun chapterFromElement(element: Element): SChapter {
        val el = element.parent()!!.parent()!!
        val chapter = SChapter.create()
        chapter.url = el.select("td:nth-child(6) a").attr("href")
        chapter.name = el.select("td:nth-child(1) a").text()
        val date = el.select("td:nth-child(3)").text()
        if (date.endsWith("ago")) {
            val splitDate = date.split(" ")
            val newDate = Calendar.getInstance()
            val amount = splitDate[0].toInt()
            when {
                splitDate[1].startsWith("min") -> {
                    newDate.add(Calendar.MINUTE, -amount)
                }
                splitDate[1].startsWith("sec") -> {
                    newDate.add(Calendar.SECOND, -amount)
                }
                splitDate[1].startsWith("hour") -> {
                    newDate.add(Calendar.HOUR, -amount)
                }
            }
            chapter.date_upload = newDate.time.time
        } else {
            chapter.date_upload = dateFormat.parse(date)?.time ?: 0L
        }
        return chapter
    }

    override fun pageListRequest(chapter: SChapter) = authenticate(GET(chapter.url, headers))

    override fun pageListParse(document: Document): List<Page> {
        val element = document.select("div#reader")
        val path = element.attr("data-path")
        val files = json.decodeFromString<JsonArray>(element.attr("data-files"))
        val pages = mutableListOf<Page>()
        for ((index, file) in files.withIndex()) {
            val url = HttpUrl.Builder()
                .scheme("https")
                .host("manga.madokami.al")
                .addPathSegments("reader/image")
                .addEncodedQueryParameter("path", URLEncoder.encode(path, "UTF-8"))
                .addEncodedQueryParameter("file", URLEncoder.encode(file.jsonPrimitive.content, "UTF-8"))
                .build()
                .toUrl()
            pages.add(Page(index, url.toExternalForm(), url.toExternalForm()))
        }
        return pages
    }

    override fun imageRequest(page: Page) = authenticate(GET(page.url, headers))

    /**
     * Returns the absolute url to the source image from the document.
     *
     * @param document the parsed document.
     */
    override fun imageUrlParse(document: Document) = ""

    override fun setupPreferenceScreen(screen: androidx.preference.PreferenceScreen) {
        val username = androidx.preference.EditTextPreference(screen.context).apply {
            key = "username"
            title = "Username"

            setOnPreferenceChangeListener { _, newValue ->
                preferences.edit().putString(key, newValue as String).commit()
            }
        }
        val password = androidx.preference.EditTextPreference(screen.context).apply {
            key = "password"
            title = "Password"

            setOnBindEditTextListener {
                it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }

            setOnPreferenceChangeListener { _, newValue ->
                preferences.edit().putString(key, newValue as String).commit()
            }
        }

        screen.addPreference(username)
        screen.addPreference(password)
    }
}
