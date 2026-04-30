package eu.kanade.tachiyomi.extension.en.madokami

import android.content.SharedPreferences
import android.text.InputType
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import java.io.IOException
import java.net.URLDecoder
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class Madokami :
    HttpSource(),
    ConfigurableSource {
    override val name = "Madokami"
    override val baseUrl = "https://manga.madokami.al"
    override val lang = "en"
    override val supportsLatest = false

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH)

    private val preferences: SharedPreferences by getPreferencesLazy()

    private fun authenticate(request: Request): Request {
        val credential = Credentials.basic(preferences.getString("username", "")!!, preferences.getString("password", "")!!)
        return request.newBuilder().header("Authorization", credential).build()
    }

    override val client: OkHttpClient = network.cloudflareClient.newBuilder().addInterceptor { chain ->
        val response = chain.proceed(chain.request())
        if (response.code == 401) throw IOException("You are currently logged out.\nGo to Extensions > Details to input your credentials.")
        response
    }.build()

    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response) = throw UnsupportedOperationException()

    override fun popularMangaRequest(page: Int): Request = authenticate(GET("$baseUrl/recent", headers))

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("table.mobile-files-table tbody tr td:nth-child(1) a:nth-child(1)").map { element ->
            mangaFromElement(element)
        }
        return MangasPage(mangas, false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .build()

        return authenticate(GET(url, headers))
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.container table tbody tr td:nth-child(1) a:nth-child(1)").map { element ->
            mangaFromElement(element)
        }
        return MangasPage(mangas, false)
    }

    private fun mangaFromElement(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.absUrl("href"))
        val pathSegments = element.absUrl("href").toHttpUrl().pathSegments
        description = URLDecoder.decode(pathSegments.last(), "UTF-8")
        var i = pathSegments.lastIndex
        while (i > 0 && URLDecoder.decode(pathSegments[i], "UTF-8").startsWith("!")) {
            i--
        }
        title = URLDecoder.decode(pathSegments[i], "UTF-8")
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        val url = (baseUrl + manga.url).toHttpUrl()
        if (url.pathSize > 5 && url.pathSegments[0] == "Manga" && url.pathSegments[1].length == 1) {
            val builder = url.newBuilder()
            for (i in 5 until url.pathSize) {
                builder.removePathSegment(5)
            }
            return authenticate(GET(builder.build().toUrl().toExternalForm(), headers))
        }
        if (url.pathSize > 2 && url.pathSegments[0] == "Raws") {
            val builder = url.newBuilder()
            var i = url.pathSize - 1
            while (url.pathSegments[i].startsWith("!") && i >= 2) {
                builder.removePathSegment(i)
                i--
            }
            return authenticate(GET(builder.build().toUrl().toExternalForm(), headers))
        }
        return authenticate(GET(url.toUrl().toExternalForm(), headers))
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            author = document.select("a[itemprop=\"author\"]").joinToString(", ") { it.text() }
            genre = document.select("div.genres a.tag").joinToString(", ") { it.text() }
            status = if (document.select("span.scanstatus").text() == "Yes") SManga.COMPLETED else SManga.UNKNOWN
            thumbnail_url = document.select("div.manga-info img[itemprop=\"image\"]").attr("abs:src")
        }
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/" + manga.url.trimStart('/')

    override fun chapterListRequest(manga: SManga) = authenticate(GET("$baseUrl/" + manga.url, headers))

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("table#index-table > tbody > tr > td:nth-child(6) > a").map { element ->
            val el = element.parent()!!.parent()!!
            SChapter.create().apply {
                url = "/reader" + el.select("td:nth-child(6) a").attr("abs:href")
                    .substringAfter("/reader")
                name = el.select("td:nth-child(1) a").text()
                val date = el.select("td:nth-child(3)").text()
                date_upload = if (date.endsWith("ago")) {
                    val splitDate = date.split(" ")
                    val cal = Calendar.getInstance()
                    val amount = splitDate[0].toInt()
                    when {
                        splitDate[1].startsWith("min") -> cal.add(Calendar.MINUTE, -amount)
                        splitDate[1].startsWith("sec") -> cal.add(Calendar.SECOND, -amount)
                        splitDate[1].startsWith("hour") -> cal.add(Calendar.HOUR, -amount)
                    }
                    cal.time.time
                } else {
                    dateFormat.tryParse(date)
                }
            }
        }.reversed()
    }

    override fun pageListRequest(chapter: SChapter): Request {
        require(chapter.url.startsWith("/")) { "Refresh chapter list" }
        return authenticate(GET(baseUrl + chapter.url, headers))
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val element = document.select("div#reader")
        val path = element.attr("data-path")
        val files = element.attr("data-files").parseAs<JsonArray>()
        return files.mapIndexed { index, file ->
            val url = HttpUrl.Builder()
                .scheme("https")
                .host("manga.madokami.al")
                .addPathSegments("reader/image")
                .addEncodedQueryParameter("path", URLEncoder.encode(path, "UTF-8"))
                .addEncodedQueryParameter("file", URLEncoder.encode(file.jsonPrimitive.content, "UTF-8"))
                .build()
                .toUrl()
            val pageUrl = url.toExternalForm()
            Page(index, url = pageUrl, imageUrl = pageUrl)
        }
    }

    override fun imageRequest(page: Page) = authenticate(GET(page.url, headers))

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

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
