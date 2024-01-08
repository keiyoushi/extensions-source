package eu.kanade.tachiyomi.multisrc.mmrcms

import android.annotation.SuppressLint
import android.net.Uri
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

abstract class MMRCMS(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
    sourceInfo: String = "",
) : HttpSource() {
    open val jsonData = if (sourceInfo == "") {
        SourceData.giveMetaData(baseUrl)
    } else {
        sourceInfo
    }

    /**
     * Parse a List of JSON sources into a list of `MyMangaReaderCMSSource`s
     *
     * Example JSON :
     * ```
     *     {
     *         "language": "en",
     *         "name": "Example manga reader",
     *         "base_url": "https://example.com",
     *         "supports_latest": true,
     *         "item_url": "https://example.com/manga/",
     *         "categories": [
     *             {"id": "stuff", "name": "Stuff"},
     *             {"id": "test", "name": "Test"}
     *         ],
     *         "tags": [
     *             {"id": "action", "name": "Action"},
     *             {"id": "adventure", "name": "Adventure"}
     *         ]
     *     }
     *
     *
     * Sources that do not supports tags may use `null` instead of a list of json objects
     *
     * @param sourceString The List of JSON strings 1 entry = one source
     * @return The list of parsed sources
     *
     * isNSFW, language, name and base_url are no longer needed as that is handled by multisrc
     * supports_latest, item_url, categories and tags are still needed
     *
     *
     */
    private val json: Json by injectLazy()
    val jsonObject = json.decodeFromString<JsonObject>(jsonData)
    override val supportsLatest = jsonObject["supports_latest"]!!.jsonPrimitive.boolean
    open val itemUrl = jsonObject["item_url"]!!.jsonPrimitive.content
    open val categoryMappings = mapToPairs(jsonObject["categories"]!!.jsonArray)
    open var tagMappings = jsonObject["tags"]?.jsonArray?.let { mapToPairs(it) } ?: emptyList()

    /**
     * Map an array of JSON objects to pairs. Each JSON object must have
     * the following properties:
     *
     * id: first item in pair
     * name: second item in pair
     *
     * @param array The array to process
     * @return The new list of pairs
     */
    open fun mapToPairs(array: JsonArray): List<Pair<String, String>> = array.map {
        it as JsonObject

        it["id"]!!.jsonPrimitive.content to it["name"]!!.jsonPrimitive.content
    }

    private val itemUrlPath = Uri.parse(itemUrl).pathSegments.firstOrNull()
    private val parsedBaseUrl = Uri.parse(baseUrl)

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(1, TimeUnit.MINUTES)
        .readTimeout(1, TimeUnit.MINUTES)
        .writeTimeout(1, TimeUnit.MINUTES)
        .build()

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/filterList?page=$page&sortBy=views&asc=false", headers)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url: Uri.Builder
        when {
            query.isNotBlank() -> {
                url = Uri.parse("$baseUrl/search")!!.buildUpon()
                url.appendQueryParameter("query", query)
            }
            else -> {
                url = Uri.parse("$baseUrl/filterList?page=$page")!!.buildUpon()
                filters.filterIsInstance<UriFilter>()
                    .forEach { it.addToUri(url) }
            }
        }
        return GET(url.toString(), headers)
    }

    /**
     * If the usual search engine isn't available, search through the list of titles with this
     */
    private fun selfSearch(query: String): Observable<MangasPage> {
        return client.newCall(GET("$baseUrl/changeMangaList?type=text", headers))
            .asObservableSuccess()
            .map { response ->
                val mangas = response.asJsoup().select("ul.manga-list a").toList()
                    .filter { it.text().contains(query, ignoreCase = true) }
                    .map {
                        SManga.create().apply {
                            title = it.text()
                            setUrlWithoutDomain(it.attr("abs:href"))
                            thumbnail_url = coverGuess(null, it.attr("abs:href"))
                        }
                    }
                MangasPage(mangas, false)
            }
    }

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/latest-release?page=$page", headers)

    override fun popularMangaParse(response: Response) = internalMangaParse(response)
    override fun searchMangaParse(response: Response): MangasPage {
        return if (listOf("query", "q").any { it in response.request.url.queryParameterNames }) {
            // If a search query was specified, use search instead!
            val jsonArray = json.decodeFromString<JsonObject>(response.body.string()).let {
                it["suggestions"]!!.jsonArray
            }
            MangasPage(
                jsonArray
                    .map {
                        SManga.create().apply {
                            val segment = it.jsonObject["data"]!!.jsonPrimitive.content
                            url = getUrlWithoutBaseUrl(itemUrl + segment)
                            title = it.jsonObject["value"]!!.jsonPrimitive.content

                            // Guess thumbnails
                            // thumbnail_url = "$baseUrl/uploads/manga/$segment/cover/cover_250x350.jpg"
                        }
                    },
                false,
            )
        } else {
            internalMangaParse(response)
        }
    }

    private val latestTitles = mutableSetOf<String>()

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()

        if (document.location().contains("page=1")) latestTitles.clear()

        val mangas = document.select(latestUpdatesSelector())
            .let { elements ->
                when {
                    // List layout (most sources)
                    elements.select("a").firstOrNull()?.hasText() == true -> elements.map { latestUpdatesFromElement(it, "a") }
                    // Grid layout (e.g. MangaID)
                    else -> document.select(gridLatestUpdatesSelector()).map { gridLatestUpdatesFromElement(it) }
                }
            }
            .filterNotNull()

        return MangasPage(mangas, document.select(latestUpdatesNextPageSelector()) != null)
    }
    private fun latestUpdatesSelector() = "div.mangalist div.manga-item"
    private fun latestUpdatesNextPageSelector() = "a[rel=next]"
    protected open fun latestUpdatesFromElement(element: Element, urlSelector: String): SManga? {
        return element.select(urlSelector).first()!!.let { titleElement ->
            if (titleElement.text() in latestTitles) {
                null
            } else {
                latestTitles.add(titleElement.text())
                SManga.create().apply {
                    url = titleElement.attr("abs:href").substringAfter(baseUrl) // intentionally not using setUrlWithoutDomain
                    title = titleElement.text().trim()
                    thumbnail_url = "$baseUrl/uploads/manga/${url.substringAfterLast('/')}/cover/cover_250x350.jpg"
                }
            }
        }
    }
    private fun gridLatestUpdatesSelector() = "div.mangalist div.manga-item, div.grid-manga tr"
    protected open fun gridLatestUpdatesFromElement(element: Element): SManga = SManga.create().apply {
        element.select("a.chart-title").let {
            setUrlWithoutDomain(it.attr("href"))
            title = it.text()
        }
        thumbnail_url = element.select("img").attr("abs:src")
    }

    protected open fun internalMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val internalMangaSelector = when (name) {
            "Utsukushii" -> "div.content div.col-sm-6"
            else -> "div[class^=col-sm], div.col-xs-6"
        }
        return MangasPage(
            document.select(internalMangaSelector).map {
                SManga.create().apply {
                    val urlElement = it.getElementsByClass("chart-title")
                    if (urlElement.size == 0) {
                        url = getUrlWithoutBaseUrl(it.select("a").attr("href"))
                        title = it.select("div.caption").text()
                        it.select("div.caption div").text().let { if (it.isNotEmpty()) title = title.substringBefore(it) } // To clean submanga's titles without breaking hentaishark's
                    } else {
                        url = getUrlWithoutBaseUrl(urlElement.attr("href"))
                        title = urlElement.text().trim()
                    }

                    it.select("img").let { img ->
                        thumbnail_url = when {
                            it.hasAttr("data-background-image") -> it.attr("data-background-image") // Utsukushii
                            img.hasAttr("data-src") -> coverGuess(img.attr("abs:data-src"), url)
                            else -> coverGuess(img.attr("abs:src"), url)
                        }
                    }
                }
            },
            document.select(".pagination a[rel=next]").isNotEmpty(),
        )
    }

    // Guess thumbnails on broken websites
    fun coverGuess(url: String?, mangaUrl: String): String? {
        return if (url?.endsWith("no-image.png") == true) {
            "$baseUrl/uploads/manga/${mangaUrl.substringAfterLast('/')}/cover/cover_250x350.jpg"
        } else {
            url
        }
    }

    fun getUrlWithoutBaseUrl(newUrl: String): String {
        val parsedNewUrl = Uri.parse(newUrl)
        val newPathSegments = parsedNewUrl.pathSegments.toMutableList()

        for (i in parsedBaseUrl.pathSegments) {
            if (i.trim().equals(newPathSegments.first(), true)) {
                newPathSegments.removeAt(0)
            } else {
                break
            }
        }

        val builtUrl = parsedNewUrl.buildUpon().path("/")
        newPathSegments.forEach { builtUrl.appendPath(it) }

        var out = builtUrl.build().encodedPath!!
        if (parsedNewUrl.encodedQuery != null) {
            out += "?" + parsedNewUrl.encodedQuery
        }
        if (parsedNewUrl.encodedFragment != null) {
            out += "#" + parsedNewUrl.encodedFragment
        }

        return out
    }

    @SuppressLint("DefaultLocale")
    override fun mangaDetailsParse(response: Response) = SManga.create().apply {
        val document = response.asJsoup()
        document.select("h2.listmanga-header, h2.widget-title").firstOrNull()?.text()?.trim()?.let { title = it }
        thumbnail_url = coverGuess(document.select(".row [class^=img-responsive]").firstOrNull()?.attr("abs:src"), document.location())
        description = document.select(".row .well p").text().trim()

        val detailAuthor = setOf("author(s)", "autor(es)", "auteur(s)", "著作", "yazar(lar)", "mangaka(lar)", "pengarang/penulis", "pengarang", "penulis", "autor", "المؤلف", "перевод", "autor/autorzy")
        val detailArtist = setOf("artist(s)", "artiste(s)", "sanatçi(lar)", "artista(s)", "artist(s)/ilustrator", "الرسام", "seniman", "rysownik/rysownicy")
        val detailGenre = setOf("categories", "categorías", "catégories", "ジャンル", "kategoriler", "categorias", "kategorie", "التصنيفات", "жанр", "kategori", "tagi")
        val detailStatus = setOf("status", "statut", "estado", "状態", "durum", "الحالة", "статус")
        val detailStatusComplete = setOf("complete", "مكتملة", "complet", "completo", "zakończone", "concluído")
        val detailStatusOngoing = setOf("ongoing", "مستمرة", "en cours", "em lançamento", "prace w toku", "ativo", "em andamento")
        val detailDescription = setOf("description", "resumen")

        for (element in document.select(".row .dl-horizontal dt")) {
            when (element.text().trim().lowercase().removeSuffix(":")) {
                in detailAuthor -> author = element.nextElementSibling()!!.text()
                in detailArtist -> artist = element.nextElementSibling()!!.text()
                in detailGenre -> genre = element.nextElementSibling()!!.select("a").joinToString {
                    it.text().trim()
                }
                in detailStatus -> status = when (element.nextElementSibling()!!.text().trim().lowercase()) {
                    in detailStatusComplete -> SManga.COMPLETED
                    in detailStatusOngoing -> SManga.ONGOING
                    else -> SManga.UNKNOWN
                }
            }
        }
        // When details are in a .panel instead of .row (ES sources)
        for (element in document.select("div.panel span.list-group-item")) {
            when (element.select("b").text().lowercase().substringBefore(":")) {
                in detailAuthor -> author = element.select("b + a").text()
                in detailArtist -> artist = element.select("b + a").text()
                in detailGenre -> genre = element.getElementsByTag("a").joinToString {
                    it.text().trim()
                }
                in detailStatus -> status = when (element.select("b + span.label").text().lowercase()) {
                    in detailStatusComplete -> SManga.COMPLETED
                    in detailStatusOngoing -> SManga.ONGOING
                    else -> SManga.UNKNOWN
                }
                in detailDescription -> description = element.ownText()
            }
        }
    }

    /**
     * Parses the response from the site and returns a list of chapters.
     *
     * Overriden to allow for null chapters
     *
     * @param response the response from the site.
     */
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select(chapterListSelector()).mapNotNull { nullableChapterFromElement(it) }
    }

    /**
     * Returns the Jsoup selector that returns a list of [Element] corresponding to each chapter.
     */
    protected open fun chapterListSelector() = "ul[class^=chapters] > li:not(.btn), table.table tr"
    // Some websites add characters after "chapters" thus the need of checking classes that starts with "chapters"

    /**
     * titleWrapper can have multiple "a" elements, filter to the first that contains letters (i.e. not "" or # as is possible)
     */
    private val urlRegex = Regex("""[a-zA-z]""")

    /**
     * Returns a chapter from the given element.
     *
     * @param element an element obtained from [chapterListSelector].
     */
    protected open fun nullableChapterFromElement(element: Element): SChapter? {
        val chapter = SChapter.create()

        try {
            val titleWrapper = element.select("[class^=chapter-title-rtl]").first()!!
            // Some websites add characters after "..-rtl" thus the need of checking classes that starts with that
            val url = titleWrapper.getElementsByTag("a")
                .first { it.attr("href").contains(urlRegex) }
                .attr("href")

            // Ensure chapter actually links to a manga
            // Some websites use the chapters box to link to post announcements
            // The check is skipped if mangas are stored in the root of the website (ex '/one-piece' without a segment like '/manga/one-piece')
            if (itemUrlPath != null && !Uri.parse(url).pathSegments.firstOrNull().equals(itemUrlPath, true)) {
                return null
            }

            chapter.url = getUrlWithoutBaseUrl(url)
            chapter.name = titleWrapper.text()

            // Parse date
            val dateText = element.getElementsByClass("date-chapter-title-rtl").text().trim()
            chapter.date_upload = parseDate(dateText)

            return chapter
        } catch (e: NullPointerException) {
            // For chapter list in a table
            if (element.select("td").hasText()) {
                element.select("td a").let {
                    chapter.setUrlWithoutDomain(it.attr("href"))
                    chapter.name = it.text()
                }
                val tableDateText = element.select("td + td").text()
                chapter.date_upload = parseDate(tableDateText)

                return chapter
            }
        }

        return null
    }

    private fun parseDate(dateText: String): Long {
        return try {
            DATE_FORMAT.parse(dateText)?.time ?: 0
        } catch (e: ParseException) {
            0L
        }
    }

    override fun pageListParse(response: Response) = response.asJsoup().select("#all > .img-responsive")
        .mapIndexed { i, e ->
            var url = (if (e.hasAttr("data-src")) e.attr("abs:data-src") else e.attr("abs:src")).trim()

            Page(i, response.request.url.toString(), url)
        }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException("Unused method called!")

    private fun getInitialFilterList() = listOf<Filter<*>>(
        Filter.Header("NOTE: Ignored if using text search!"),
        Filter.Separator(),
        AuthorFilter(),
        UriSelectFilter(
            "Category",
            "cat",
            arrayOf(
                "" to "Any",
                *categoryMappings.toTypedArray(),
            ),
        ),
        UriSelectFilter(
            "Begins with",
            "alpha",
            arrayOf(
                "" to "Any",
                *"#ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray().map {
                    Pair(it.toString(), it.toString())
                }.toTypedArray(),
            ),
        ),
        SortFilter(),
    )

    /**
     * Returns the list of filters for the source.
     */
    override fun getFilterList(): FilterList {
        return when {
            tagMappings != emptyList<Pair<String, String>>() -> {
                FilterList(
                    getInitialFilterList() + UriSelectFilter(
                        "Tag",
                        "tag",
                        arrayOf(
                            "" to "Any",
                            *tagMappings.toTypedArray(),
                        ),
                    ),
                )
            }
            else -> FilterList(getInitialFilterList())
        }
    }

    /**
     * Class that creates a select filter. Each entry in the dropdown has a name and a display name.
     * If an entry is selected it is appended as a query parameter onto the end of the URI.
     * If `firstIsUnspecified` is set to true, if the first entry is selected, nothing will be appended on the the URI.
     */
    // vals: <name, display>
    open class UriSelectFilter(
        displayName: String,
        private val uriParam: String,
        private val vals: Array<Pair<String, String>>,
        private val firstIsUnspecified: Boolean = true,
        defaultValue: Int = 0,
    ) :
        Filter.Select<String>(displayName, vals.map { it.second }.toTypedArray(), defaultValue), UriFilter {
        override fun addToUri(uri: Uri.Builder) {
            if (state != 0 || !firstIsUnspecified) {
                uri.appendQueryParameter(uriParam, vals[state].first)
            }
        }
    }

    class AuthorFilter : Filter.Text("Author"), UriFilter {
        override fun addToUri(uri: Uri.Builder) {
            uri.appendQueryParameter("author", state)
        }
    }

    class SortFilter :
        Filter.Sort(
            "Sort",
            sortables.map { it.second }.toTypedArray(),
            Selection(0, true),
        ),
        UriFilter {
        override fun addToUri(uri: Uri.Builder) {
            uri.appendQueryParameter("sortBy", sortables[state!!.index].first)
            uri.appendQueryParameter("asc", state!!.ascending.toString())
        }

        companion object {
            private val sortables = arrayOf(
                "name" to "Name",
                "views" to "Popularity",
                "last_release" to "Last update",
            )
        }
    }

    /**
     * Represents a filter that is able to modify a URI.
     */
    interface UriFilter {
        fun addToUri(uri: Uri.Builder)
    }

    companion object {
        private val DATE_FORMAT = SimpleDateFormat("d MMM. yyyy", Locale.US)
    }
}
