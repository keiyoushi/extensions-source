package eu.kanade.tachiyomi.multisrc.nepnep

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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Source responds to requests with their full database as a JsonArray, then sorts/filters it client-side
 * We'll take the database on first requests, then do what we want with it
 */
abstract class NepNep(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
) : HttpSource() {

    override val supportsLatest = true

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("Referer", "$baseUrl/")
        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:71.0) Gecko/20100101 Firefox/77.0")

    private val json: Json by injectLazy()

    private lateinit var directory: List<JsonElement>

    // Convenience functions to shorten later code
    /** Returns value corresponding to given key as a string, or null */
    private fun JsonElement.getString(key: String): String? {
        return this.jsonObject[key]!!.jsonPrimitive.contentOrNull
    }

    /** Returns value corresponding to given key as a JsonArray */
    private fun JsonElement.getArray(key: String): JsonArray {
        return this.jsonObject[key]!!.jsonArray
    }

    // Popular

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        return if (page == 1) {
            client.newCall(popularMangaRequest(page))
                .asObservableSuccess()
                .map { response ->
                    popularMangaParse(response)
                }
        } else {
            Observable.just(parseDirectory(page))
        }
    }

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/search/", headers)
    }

    // don't use ";" for substringBefore() !
    private fun directoryFromDocument(document: Document): JsonArray {
        val str = document.select("script:containsData(MainFunction)").first()!!.data()
            .substringAfter("vm.Directory = ").substringBefore("vm.GetIntValue").trim()
            .replace(";", " ")
        return json.parseToJsonElement(str).jsonArray
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        thumbnailUrl = document.select(".SearchResult > .SearchResultCover img").first()!!.attr("ng-src")
        directory = directoryFromDocument(document).sortedByDescending { it.getString("v") }
        return parseDirectory(1)
    }

    private fun parseDirectory(page: Int): MangasPage {
        val mangas = mutableListOf<SManga>()
        val endRange = ((page * 24) - 1).let { if (it <= directory.lastIndex) it else directory.lastIndex }

        for (i in (((page - 1) * 24)..endRange)) {
            mangas.add(
                SManga.create().apply {
                    title = directory[i].getString("s")!!
                    url = "/manga/${directory[i].getString("i")}"
                    thumbnail_url = getThumbnailUrl(directory[i].getString("i")!!)
                },
            )
        }
        return MangasPage(mangas, endRange < directory.lastIndex)
    }

    private var thumbnailUrl: String? = null

    private fun getThumbnailUrl(id: String): String {
        if (thumbnailUrl.isNullOrEmpty()) {
            val response = client.newCall(popularMangaRequest(1)).execute()
            thumbnailUrl = response.asJsoup().select(".SearchResult > .SearchResultCover img").first()!!.attr("ng-src")
        }

        return thumbnailUrl!!.replace("{{Result.i}}", id)
    }

    // Latest

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
        return if (page == 1) {
            client.newCall(latestUpdatesRequest(page))
                .asObservableSuccess()
                .map { response ->
                    latestUpdatesParse(response)
                }
        } else {
            Observable.just(parseDirectory(page))
        }
    }

    override fun latestUpdatesRequest(page: Int): Request = popularMangaRequest(1)

    override fun latestUpdatesParse(response: Response): MangasPage {
        directory = directoryFromDocument(response.asJsoup()).sortedByDescending { it.getString("lt") }
        return parseDirectory(1)
    }

    // Search

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return if (page == 1) {
            client.newCall(searchMangaRequest(page, query, filters))
                .asObservableSuccess()
                .map { response ->
                    searchMangaParse(response, query, filters)
                }
        } else {
            Observable.just(parseDirectory(page))
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = popularMangaRequest(1)

    private fun searchMangaParse(response: Response, query: String, filters: FilterList): MangasPage {
        val trimmedQuery = query.trim()
        directory = directoryFromDocument(response.asJsoup())
            .filter {
                // Comparing query with display name
                it.getString("s")!!.contains(trimmedQuery, ignoreCase = true) or
                    // Comparing query with list of alternate names
                    it.getArray("al").any { altName ->
                        altName.jsonPrimitive.content.contains(trimmedQuery, ignoreCase = true)
                    }
            }

        val genres = mutableListOf<String>()
        val genresNo = mutableListOf<String>()
        var sortBy: String
        for (filter in if (filters.isEmpty()) getFilterList() else filters) {
            when (filter) {
                is Sort -> {
                    sortBy = when (filter.state?.index) {
                        1 -> "ls"
                        2 -> "v"
                        else -> "s"
                    }
                    directory = if (filter.state?.ascending != true) {
                        directory.sortedByDescending { it.getString(sortBy) }
                    } else {
                        directory.sortedByDescending { it.getString(sortBy) }.reversed()
                    }
                }
                is SelectField -> if (filter.state != 0) {
                    directory = when (filter.name) {
                        "Scan Status" -> directory.filter { it.getString("ss")!!.contains(filter.values[filter.state], ignoreCase = true) }
                        "Publish Status" -> directory.filter { it.getString("ps")!!.contains(filter.values[filter.state], ignoreCase = true) }
                        "Type" -> directory.filter { it.getString("t")!!.contains(filter.values[filter.state], ignoreCase = true) }
                        "Translation" -> directory.filter { it.getString("o")!!.contains("yes", ignoreCase = true) }
                        else -> directory
                    }
                }
                is YearField -> if (filter.state.isNotEmpty()) directory = directory.filter { it.getString("y")!!.contains(filter.state) }
                is AuthorField -> if (filter.state.isNotEmpty()) {
                    directory = directory.filter { e ->
                        e.getArray("a").any {
                            it.jsonPrimitive.content.contains(filter.state, ignoreCase = true)
                        }
                    }
                }
                is GenreList -> filter.state.forEach { genre ->
                    when (genre.state) {
                        Filter.TriState.STATE_INCLUDE -> genres.add(genre.name)
                        Filter.TriState.STATE_EXCLUDE -> genresNo.add(genre.name)
                    }
                }
                else -> continue
            }
        }
        if (genres.isNotEmpty()) {
            genres.map { genre ->
                directory = directory.filter { e ->
                    e.getArray("g").any { it.jsonPrimitive.content.contains(genre, ignoreCase = true) }
                }
            }
        }
        if (genresNo.isNotEmpty()) {
            genresNo.map { genre ->
                directory = directory.filterNot { e ->
                    e.getArray("g").any { it.jsonPrimitive.content.contains(genre, ignoreCase = true) }
                }
            }
        }

        return parseDirectory(1)
    }

    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException("Not used")

    // Details

    override fun mangaDetailsParse(response: Response): SManga {
        return response.asJsoup().select("div.BoxBody > div.row").let { info ->
            SManga.create().apply {
                title = info.select("h1").text()
                author = info.select("li.list-group-item:has(span:contains(Author)) a").first()?.text()
                status = info.select("li.list-group-item:has(span:contains(Status)) a:contains(scan)").text().toStatus()
                description = info.select("div.Content").text()
                thumbnail_url = info.select("img").attr("abs:src")

                val genres = info.select("li.list-group-item:has(span:contains(Genre)) a")
                    .map { element -> element.text() }
                    .toMutableSet()

                // add series type(manga/manhwa/manhua/other) thinggy to genre
                info.select("li.list-group-item:has(span:contains(Type)) a, a[href*=type\\=]").firstOrNull()?.ownText()?.let {
                    if (it.isEmpty().not()) {
                        genres.add(it)
                    }
                }

                genre = genres.toList().joinToString(", ")

                // add alternative name to manga description
                val altName = "Alternative Name: "
                info.select("li.list-group-item:has(span:contains(Alter))").firstOrNull()?.ownText()?.let {
                    if (it.isBlank().not() && it != "N/A") {
                        description = when {
                            description.isNullOrBlank() -> altName + it
                            else -> description + "\n\n$altName" + it
                        }
                    }
                }
            }
        }
    }

    private fun String.toStatus() = when {
        this.contains("Ongoing", ignoreCase = true) -> SManga.ONGOING
        this.contains("Complete", ignoreCase = true) -> SManga.COMPLETED
        this.contains("Cancelled", ignoreCase = true) -> SManga.CANCELLED
        this.contains("Hiatus", ignoreCase = true) -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    // Chapters - Mind special cases like decimal chapters (e.g. One Punch Man) and manga with seasons (e.g. The Gamer)

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:SS Z", Locale.getDefault())

    private fun chapterURLEncode(e: String): String {
        var index = ""
        val t = e.substring(0, 1).toInt()
        if (1 != t) { index = "-index-$t" }
        val dgt = if (e.toInt() < 100100) { 4 } else if (e.toInt() < 101000) { 3 } else if (e.toInt() < 110000) { 2 } else { 1 }
        val n = e.substring(dgt, e.length - 1)
        var suffix = ""
        val path = e.substring(e.length - 1).toInt()
        if (0 != path) { suffix = ".$path" }
        return "-chapter-$n$suffix$index.html"
    }

    private val chapterImageRegex = Regex("""^0+""")

    private fun chapterImage(e: String, cleanString: Boolean = false): String {
        // cleanString will result in an empty string if chapter number is 0, hence the else if below
        val a = e.substring(1, e.length - 1).let { if (cleanString) it.replace(chapterImageRegex, "") else it }
        // If b is not zero, indicates chapter has decimal numbering
        val b = e.substring(e.length - 1).toInt()
        return if (b == 0 && a.isNotEmpty()) {
            a
        } else if (b == 0 && a.isEmpty()) {
            "0"
        } else {
            "$a.$b"
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val vmChapters = response.asJsoup().select("script:containsData(MainFunction)").first()!!.data()
            .substringAfter("vm.Chapters = ").substringBefore(";")
        return json.parseToJsonElement(vmChapters).jsonArray.map { json ->
            val indexChapter = json.getString("Chapter")!!
            SChapter.create().apply {
                name = json.getString("ChapterName").let { if (it.isNullOrEmpty()) "${json.getString("Type")} ${chapterImage(indexChapter, true)}" else it }
                url = "/read-online/" + response.request.url.toString().substringAfter("/manga/") + chapterURLEncode(indexChapter)
                date_upload = try {
                    json.getString("Date").let { dateFormat.parse("$it +0600")?.time } ?: 0
                } catch (_: Exception) {
                    0L
                }
            }
        }
    }

    // Pages

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val script = document.selectFirst("script:containsData(MainFunction)")?.data()
            ?: client.newCall(GET(document.location().removeSuffix(".html"), headers))
                .execute().asJsoup().selectFirst("script:containsData(MainFunction)")!!.data()
        val curChapter = json.parseToJsonElement(script!!.substringAfter("vm.CurChapter = ").substringBefore(";")).jsonObject

        val pageTotal = curChapter.getString("Page")!!.toInt()

        val host = "https://" +
            script
                .substringAfter("vm.CurPathName = \"", "")
                .substringBefore("\"")
                .also {
                    if (it.isEmpty()) {
                        throw Exception("$name is overloaded and blocking Tachiyomi right now. Wait for unblock.")
                    }
                }
        val titleURI = script.substringAfter("vm.IndexName = \"").substringBefore("\"")
        val seasonURI = curChapter.getString("Directory")!!
            .let { if (it.isEmpty()) "" else "$it/" }
        val path = "$host/manga/$titleURI/$seasonURI"

        val chNum = chapterImage(curChapter.getString("Chapter")!!)

        return IntRange(1, pageTotal).mapIndexed { i, _ ->
            val imageNum = (i + 1).toString().let { "000$it" }.let { it.substring(it.length - 3) }
            Page(i, "", "$path$chNum-$imageNum.png")
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Not used")

    // Filters

    private class Sort : Filter.Sort("Sort", arrayOf("Alphabetically", "Date updated", "Popularity"), Selection(2, false))
    private class Genre(name: String) : Filter.TriState(name)
    private class YearField : Filter.Text("Years")
    private class AuthorField : Filter.Text("Author")
    private class SelectField(name: String, values: Array<String>, state: Int = 0) : Filter.Select<String>(name, values, state)
    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Genres", genres)

    override fun getFilterList() = FilterList(
        YearField(),
        AuthorField(),
        SelectField("Scan Status", arrayOf("Any", "Complete", "Discontinued", "Hiatus", "Incomplete", "Ongoing")),
        SelectField("Publish Status", arrayOf("Any", "Cancelled", "Complete", "Discontinued", "Hiatus", "Incomplete", "Ongoing", "Unfinished")),
        SelectField("Type", arrayOf("Any", "Doujinshi", "Manga", "Manhua", "Manhwa", "OEL", "One-shot")),
        SelectField("Translation", arrayOf("Any", "Official Only")),
        Sort(),
        GenreList(getGenreList()),
    )

    // [...document.querySelectorAll("label.triStateCheckBox input")].map(el => `Filter("${el.getAttribute('name')}", "${el.nextSibling.textContent.trim()}")`).join(',\n')
    // https://manga4life.com/advanced-search/
    private fun getGenreList() = listOf(
        Genre("Action"),
        Genre("Adult"),
        Genre("Adventure"),
        Genre("Comedy"),
        Genre("Doujinshi"),
        Genre("Drama"),
        Genre("Ecchi"),
        Genre("Fantasy"),
        Genre("Gender Bender"),
        Genre("Harem"),
        Genre("Hentai"),
        Genre("Historical"),
        Genre("Horror"),
        Genre("Isekai"),
        Genre("Josei"),
        Genre("Lolicon"),
        Genre("Martial Arts"),
        Genre("Mature"),
        Genre("Mecha"),
        Genre("Mystery"),
        Genre("Psychological"),
        Genre("Romance"),
        Genre("School Life"),
        Genre("Sci-fi"),
        Genre("Seinen"),
        Genre("Shotacon"),
        Genre("Shoujo"),
        Genre("Shoujo Ai"),
        Genre("Shounen"),
        Genre("Shounen Ai"),
        Genre("Slice of Life"),
        Genre("Smut"),
        Genre("Sports"),
        Genre("Supernatural"),
        Genre("Tragedy"),
        Genre("Yaoi"),
        Genre("Yuri"),
    )
}
