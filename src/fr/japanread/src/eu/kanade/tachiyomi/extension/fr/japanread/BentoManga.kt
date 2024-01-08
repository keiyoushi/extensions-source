package eu.kanade.tachiyomi.extension.fr.japanread

import android.app.Application
import android.net.Uri
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.util.Calendar
import java.util.concurrent.TimeUnit

class BentoManga : ParsedHttpSource(), ConfigurableSource {

    override val name = "Bento Manga"

    override val id: Long = 4697148576707003393

    override val baseUrl = "https://www.bentomanga.com"

    override val lang = "fr"

    override val supportsLatest = true

    private val json: Json by injectLazy()

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .rateLimit(2, 1)
        .build()

    override fun headersBuilder(): Headers.Builder {
        val builder = super.headersBuilder().apply {
            set("Referer", "$baseUrl/")

            // Headers for homepage + serie page
            set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
            set("Accept-Language", "fr,fr-FR;q=0.8,en-US;q=0.5,en;q=0.3")
            set("Connection", "keep-alive")
            set("Sec-Fetch-Dest", "document")
            set("Sec-Fetch-Mode", "navigate")
            set("Sec-Fetch-Site", "same-origin")
            set("Sec-Fetch-User", "?1")
        }

        val preferences = Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
        val userAgent = preferences.getString(USER_AGENT_PREF, "")!!
        return if (userAgent.isNotBlank()) {
            builder.set("User-Agent", userAgent)
        } else {
            builder
        }
    }

    // Generic (used by popular/latest/search)
    private fun mangaListFromElement(element: Element): SManga {
        return SManga.create().apply {
            title = element.select("div").select("div.manga_header h1")
                .text()
            setUrlWithoutDomain(element.select("a").attr("href"))
            thumbnail_url = element.select("div").select("img[alt=couverture manga]")
                .attr("src")
        }
    }

    private fun mangaListSelector() = "div#mangas_content div.manga"
    private fun mangaListNextPageSelector() = ".paginator button:contains(>)"

    // Popular
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/manga_list?withoutTypes=5&order_by=views&limit=" + (page - 1), headers)
    }

    override fun popularMangaSelector() = mangaListSelector()
    override fun popularMangaFromElement(element: Element) = mangaListFromElement(element)
    override fun popularMangaNextPageSelector() = mangaListNextPageSelector()

    // Latest
    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/manga_list?withoutTypes=5&limit=" + (page - 1), headers)
    }

    override fun latestUpdatesSelector() = mangaListSelector()
    override fun latestUpdatesFromElement(element: Element) = mangaListFromElement(element)
    override fun latestUpdatesNextPageSelector() = mangaListNextPageSelector()

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        // If there is any search text, use text search, otherwise use filter search
        val uri = if (query.isNotBlank()) {
            Uri.parse("$baseUrl/manga_list?withoutTypes=5")
                .buildUpon()
                .appendQueryParameter("search", query)
        } else {
            val uri = Uri.parse("$baseUrl/manga_list?withoutTypes=5").buildUpon()
            // Append uri filters
            filters.forEach {
                if (it is UriFilter) {
                    it.addToUri(uri)
                }
            }
            uri
        }
        // Append page number
        uri.appendQueryParameter("limit", (page - 1).toString())
        return GET(uri.toString())
    }

    override fun searchMangaSelector() = mangaListSelector()
    override fun searchMangaFromElement(element: Element) = mangaListFromElement(element)
    override fun searchMangaNextPageSelector() = mangaListNextPageSelector()

    // Details

    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            title = document.select("div.manga div.manga-infos div.component-manga-title div.component-manga-title_main h1 ")
                .text()
            artist = document.select("div.datas div.datas_more-artists div.datas_more-artists-people a").text()
            author = document.select("div.datas div.datas_more-authors div.datas_more-authors-peoples div a").text()
            description = document.select("div.datas div.datas_synopsis").text()
            genre = document.select("div.manga div.manga-infos div.component-manga-categories a")
                .joinToString(" , ") { it.text() }
            status = document.select("div.datas div.datas_more div.datas_more-status div.datas_more-status-data")?.first()?.text()?.let {
                when {
                    it.contains("En cours") -> SManga.ONGOING
                    it.contains("Terminé") -> SManga.COMPLETED
                    it.contains("En pause") -> SManga.ON_HIATUS
                    it.contains("Licencié") -> SManga.LICENSED
                    it.contains("Abandonné") -> SManga.CANCELLED
                    else -> SManga.UNKNOWN
                }
            } ?: SManga.UNKNOWN

            thumbnail_url = document.select("img[alt=couverture manga]").attr("src")
        }
    }

    private fun apiHeaders(refererURL: String) = headers.newBuilder().apply {
        set("Referer", refererURL)
        set("x-requested-with", "XMLHttpRequest")
        // without this we get 404 but I don't know why, I cannot find any information about this 'a' header.
        // In chrome the value is constantly changing on each request, but giving this fixed value seems to work
        set("a", "1df19bce590b")
    }.build()

    // Chapters
    // Subtract relative date
    private fun parseRelativeDate(date: String): Long {
        val trimmedDate = date.substringAfter("Il y a").trim().split(" ")

        val calendar = Calendar.getInstance()
        when (trimmedDate[1]) {
            "ans" -> calendar.apply { add(Calendar.YEAR, -trimmedDate[0].toInt()) }
            "an" -> calendar.apply { add(Calendar.YEAR, -trimmedDate[0].toInt()) }
            "mois" -> calendar.apply { add(Calendar.MONTH, -trimmedDate[0].toInt()) }
            "sem." -> calendar.apply { add(Calendar.WEEK_OF_MONTH, -trimmedDate[0].toInt()) }
            "j" -> calendar.apply { add(Calendar.DAY_OF_MONTH, -trimmedDate[0].toInt()) }
            "h" -> calendar.apply { add(Calendar.HOUR_OF_DAY, -trimmedDate[0].toInt()) }
            "min" -> calendar.apply { add(Calendar.MINUTE, -trimmedDate[0].toInt()) }
            "s" -> calendar.apply { add(Calendar.SECOND, 0) }
        }

        return calendar.timeInMillis
    }

    override fun chapterListSelector() = "div.page_content div.chapters_content div.div-item"

    override fun chapterFromElement(element: Element): SChapter {
        return SChapter.create().apply {
            name = element.select("div.component-chapter-title a span.chapter_volume").text()
            setUrlWithoutDomain(element.select("div.component-chapter-title a:not([style*='display:none'])").attr("href"))
            date_upload = parseRelativeDate(element.select("div.component-chapter-date").text())
            scanlator = element.select("div.component-chapter-teams a span").joinToString(" + ") { it.text() }
        }
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val requestUrl = if (manga.url.startsWith("http")) {
            "${manga.url}"
        } else {
            "$baseUrl${manga.url}"
        }
        return client.newCall(GET(requestUrl, headers))
            .asObservableSuccess()
            .map { response ->
                chapterListParse(response, requestUrl)
            }
    }

    private fun chapterListParse(response: Response, requestUrl: String): List<SChapter> {
        val chapters = mutableListOf<SChapter>()
        var document = response.asJsoup()
        var moreChapters = true
        var nextPage = 1
        val pagemax = if (!document.select(".paginator button:contains(>>)").isNullOrEmpty()) {
            document.select(".paginator button:contains(>>)")?.first()?.attr("data-limit")?.toInt()?.plus(1)
                ?: 1
        } else {
            1
        }
        // chapters are paginated
        while (moreChapters && nextPage <= pagemax) {
            document.select(chapterListSelector()).map { chapters.add(chapterFromElement(it)) }
            if (nextPage < pagemax) {
                document = client.newCall(GET("$requestUrl?limit=$nextPage", headers)).execute().asJsoup()
                nextPage++
            } else {
                moreChapters = false
            }
        }
        return chapters
    }

    // Alternative way through API in case jSoup doesn't work anymore
    // It gives precise timestamp, but we are not using it
    // since the API wrongly returns null for the scanlation group
    /*private fun getChapterName(jsonElement: JsonElement): String {
        var name = ""

        if (jsonElement["volume"].asString != "") {
            name += "Tome " + jsonElement["volume"].asString + " "
        }
        if (jsonElement["chapter"].asString != "") {
            name += "Ch " + jsonElement["chapter"].asString + " "
        }

        if (jsonElement["title"].asString != "") {
            if (name != "") {
                name += " - "
            }
            name += jsonElement["title"].asString
        }

        return name
    }
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val mangaId = document.select("div[data-avg]").attr("data-avg")

        client.newCall(GET(baseUrl + document.select("#chapters div[data-row=chapter]").first()!!.select("div.col-lg-5 a").attr("href"), headers)).execute()

        val apiResponse = client.newCall(GET("$baseUrl/api/?id=$mangaId&type=manga", apiHeaders())).execute()

        val jsonData = apiResponse.body.string()
        val json = JsonParser().parse(jsonData).asJsonObject

        return json["chapter"].obj.entrySet()
            .map {
                SChapter.create().apply {
                    name = getChapterName(it.value.obj)
                    url = "$baseUrl/api/?id=${it.key}&type=chapter"
                    date_upload = it.value.obj["timestamp"].asLong * 1000
                    // scanlator = element.select(".chapter-list-group a").joinToString { it.text() }
                }
            }
            .sortedByDescending { it.date_upload }
    }
    override fun chapterListSelector() = throw UnsupportedOperationException("Not Used")
    override fun chapterFromElement(element: Element): SChapter = throw UnsupportedOperationException("Not Used")*/

    // Pages
    override fun pageListRequest(chapter: SChapter): Request = GET("$baseUrl${chapter.url}", headers)

    override fun pageListParse(document: Document): List<Page> {
        val chapterId = document.select("meta[data-chapter-id]").attr("data-chapter-id")

        val apiRequest = GET("$baseUrl/api/?id=$chapterId&type=chapter", apiHeaders(document.location()))
        val apiResponse = client.newCall(apiRequest).execute()

        val jsonResult = json.parseToJsonElement(apiResponse.body.string()).jsonObject

        val baseImagesUrl = jsonResult["baseImagesUrl"]!!.jsonPrimitive.content

        return jsonResult["page_array"]!!.jsonArray.mapIndexed { i, jsonEl ->
            Page(i, document.location(), "$baseUrl$baseImagesUrl/${jsonEl.jsonPrimitive.content}")
        }
    }

    override fun imageUrlParse(document: Document) = ""

    override fun imageRequest(page: Page): Request {
        val newHeaders = headers.newBuilder().apply {
            set("Referer", page.url)
            set("Accept", "image/avif,image/webp,*/*")
            set("Accept-Language", "fr,fr-FR;q=0.8,en-US;q=0.5,en;q=0.3")
            set("Connection", "keep-alive")
            set("Sec-Fetch-Dest", "document")
            set("Sec-Fetch-Mode", "navigate")
            set("Sec-Fetch-Site", "same-origin")
            set("Sec-Fetch-User", "?1")
        }.build()

        return GET(page.imageUrl!!, newHeaders)
    }

    // Filters
    override fun getFilterList() = FilterList(
        SortFilter(),
        TypeFilter(),
        StatusFilter(),
        GenreFilter(),
    )

    private class SortFilter : UriSelectFilter(
        "Tri",
        "order_by",
        arrayOf(
            Pair("views", "Les + vus"),
            Pair("top", "Les mieux notés"),
            Pair("name", "A - Z"),
            Pair("comment", "Les + commentés"),
            Pair("update", "Les + récents"),
            Pair("create", "Par date de sortie"),
        ),
        firstIsUnspecified = false,
    )

    private class TypeFilter : UriSelectFilter(
        "Type",
        "withTypes",
        arrayOf(
            Pair("0", "Tous"),
            Pair("2", "Manga"),
            Pair("3", "Manhwa"),
            Pair("4", "Manhua"),
            Pair("5", "Novel"),
            Pair("6", "Doujinshi"),
        ),
    )

    private class StatusFilter : UriSelectFilter(
        "Statut",
        "status",
        arrayOf(
            Pair("0", "Tous"),
            Pair("1", "En cours"),
            Pair("2", "Terminé"),
            Pair("3", "En pause"),
            Pair("4", "Licencié"),
            Pair("5", "Abandonné"),
        ),
    )

    private class GenreFilter : UriSelectFilter(
        "Genre",
        "withCategories",
        arrayOf(
            Pair("0", "Tous"),
            Pair("1", "Action"),
            Pair("27", "Adulte"),
            Pair("20", "Amitié"),
            Pair("21", "Amour"),
            Pair("7", "Arts martiaux"),
            Pair("3", "Aventure"),
            Pair("6", "Combat"),
            Pair("5", "Comédie"),
            Pair("4", "Drame"),
            Pair("12", "Ecchi"),
            Pair("16", "Fantastique"),
            Pair("29", "Gender Bender"),
            Pair("8", "Guerre"),
            Pair("22", "Harem"),
            Pair("23", "Hentai"),
            Pair("15", "Historique"),
            Pair("19", "Horreur"),
            Pair("13", "Josei"),
            Pair("30", "Mature"),
            Pair("18", "Mecha"),
            Pair("32", "One-shot"),
            Pair("42", "Parodie"),
            Pair("17", "Policier"),
            Pair("25", "Science-fiction"),
            Pair("31", "Seinen"),
            Pair("10", "Shojo"),
            Pair("26", "Shojo Ai"),
            Pair("2", "Shonen"),
            Pair("35", "Shonen Ai"),
            Pair("37", "Smut"),
            Pair("14", "Sports"),
            Pair("38", "Surnaturel"),
            Pair("39", "Tragédie"),
            Pair("36", "Tranches de vie"),
            Pair("34", "Vie scolaire"),
            Pair("24", "Yaoi"),
            Pair("41", "Yuri"),
        ),
    )

    /**
     * Class that creates a select filter. Each entry in the dropdown has a name and a display name.
     * If an entry is selected it is appended as a query parameter onto the end of the URI.
     * If `firstIsUnspecified` is set to true, if the first entry is selected, nothing will be appended on the the URI.
     */
    // vals: <name, display>
    private open class UriSelectFilter(
        displayName: String,
        val uriParam: String,
        val vals: Array<Pair<String, String>>,
        val firstIsUnspecified: Boolean = true,
        defaultValue: Int = 0,
    ) :
        Filter.Select<String>(displayName, vals.map { it.second }.toTypedArray(), defaultValue),
        UriFilter {
        override fun addToUri(uri: Uri.Builder) {
            if (state != 0 || !firstIsUnspecified) {
                uri.appendQueryParameter(uriParam, vals[state].first)
            }
        }
    }

    /**
     * Represents a filter that is able to modify a URI.
     */
    private interface UriFilter {
        fun addToUri(uri: Uri.Builder)
    }

    // From Happymh for the custom User-Agent menu
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        // Maybe add the choice of a random UA ? (Like Mangathemesia)

        EditTextPreference(screen.context).apply {
            key = USER_AGENT_PREF
            title = TITLE_RANDOM_UA
            summary = USER_AGENT_PREF
            dialogMessage =
                "\n\nPermet d'indiquer un User-Agent custom\n" +
                "Après l'ajout + restart de l'application, il faudra charger la page en webview et valider le captcha Cloudflare." +
                "\n\nValeur par défaut:\n$DEFAULT_UA"

            setDefaultValue(DEFAULT_UA)

            setOnPreferenceChangeListener { _, newValue ->
                try {
                    Headers.Builder().add("User-Agent", newValue as String)
                    Toast.makeText(screen.context, RESTART_APP_STRING, Toast.LENGTH_LONG).show()
                    summary = newValue
                    true
                } catch (e: Throwable) {
                    Toast.makeText(screen.context, "$ERROR_USER_AGENT_SETUP ${e.message}", Toast.LENGTH_LONG).show()
                    false
                }
            }
        }.let(screen::addPreference)
    }

    companion object {
        private const val USER_AGENT_PREF = "Empty"
        private const val RESTART_APP_STRING = "Restart Tachiyomi to apply new setting."
        private const val ERROR_USER_AGENT_SETUP = "Invalid User-Agent :"
        private const val TITLE_RANDOM_UA = "Set custom User-Agent"
        private const val DEFAULT_UA = "Mozilla/5.0 (Linux; Android 9) AppleWebKit/537.36 (KHTML, like Gecko) Brave/107.0.0.0 Mobile Safari/537.36"
    }
}
