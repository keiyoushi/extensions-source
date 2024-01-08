package eu.kanade.tachiyomi.extension.en.mangadoom

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.CacheControl
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.io.IOException
import java.nio.charset.Charset
import java.util.Calendar

class MangaDoom : HttpSource() {

    override val baseUrl = "https://www.mngdoom.com"
    override val lang = "en"
    override val name = "MangaDoom"
    override val supportsLatest = true

    private val popularMangaPath = "/popular-manga/"

    private val popularMangaSelector = "div.row.manga-list-style"

    // popular
    override fun popularMangaRequest(page: Int) = GET(baseUrl + popularMangaPath + page)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        return MangasPage(
            document.select(popularMangaSelector).map {
                mangaFromMangaListElement(it)
            },
            paginationHasNext(document),
        )
    }

    // latest
    private val latestMangaPath = "/latest-chapters"

    /**
     * The website has a pagination problem for the latest-chapters list.
     * latest-chapters/ without a page number is the first page, latest-chapters/1 is the
     * second page, latest-chapters/2 is the third page, ....
     */
    override fun latestUpdatesRequest(page: Int): Request {
        var url = baseUrl + latestMangaPath

        if (page != 1) {
            url += "/${page - 1}"
        }

        return GET(url)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangaUpdates = document.select("div.manga_updates > dl > div.manga-cover > a")

        return MangasPage(
            mangaUpdates.map { mangaFromMangaTitleElement(it) },
            paginationHasNext(document),
        )
    }

    /**
     * Checks on a page that has pagination (e.g. popular-manga and latest-chapters)
     * whether or not a next page exists.
     */
    private fun paginationHasNext(document: Document) = !document
        .select("ul.pagination > li:contains(»)").isEmpty()

    // individual manga
    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        val innerContentElement = document.select("div.content-inner.inner-page").first()!!
        val dlElement = innerContentElement.select("div.col-md-8 > dl").first()!!

        return SManga.create().apply {
            this.url = response.request.url.toString()

            this.title = innerContentElement
                .select("h5.widget-heading:matchText").first()!!.text()
            this.thumbnail_url = innerContentElement
                .select("div.col-md-4 > img").first()?.attr("src")

            this.genre = dlElement.select("dt:contains(Categories:) ~ dd > a[title]")
                .joinToString { e -> e.attr("title") }

            this.description = innerContentElement.select("div.note").first()?.let {
                descriptionProcessor(it)
            }

            this.author = dlElement.selectFirst("dt:contains(Author:) ~ dd")
                ?.text().takeIf { it != "-" }

            this.artist = dlElement.selectFirst("dt:contains(Artist:) ~ dd")
                ?.text().takeIf { it != "-" }

            this.status = when (
                dlElement.selectFirst("dt:contains(Status:) ~ dd")
                    ?.text()
            ) {
                "Ongoing" -> SManga.ONGOING
                "Completed" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }
    }

    /**
     * Manga descriptions are composed of a multitude of (sometimes nested) html-elements + free
     * text and seemingly follow no common structure.
     * This function is used for parsing the html manga description into a String
     */
    private fun descriptionProcessor(descriptionRootNode: Node): String? {
        val descriptionStringBuilder = StringBuilder()

        /**
         * Determines which String best represents a single html node.
         * Does not care about any hierarchy (neither siblings nor children)
         */
        fun descriptionElementProcessor(descriptionNode: Node): String? {
            if (descriptionNode is Element) {
                if (descriptionNode.tagName() == "br") {
                    return "\n"
                }
            } else if (descriptionNode is TextNode) {
                return descriptionNode.text()
            }

            return null
        }

        /**
         * Responsible for the flow of the description.
         * Manages the description hierarchy.
         */
        fun descriptionHierarchyProcessor(currentNode: Node) {
            descriptionElementProcessor(currentNode)?.let {
                descriptionStringBuilder.append(it)
            }

            val childNodesIterator = currentNode.childNodes().iterator()

            while (childNodesIterator.hasNext()) {
                descriptionHierarchyProcessor(childNodesIterator.next())
            }

            if (currentNode is Element && currentNode.tagName() == "p") {
                descriptionStringBuilder.append("\n\n")
            }
        }

        descriptionHierarchyProcessor(descriptionRootNode)

        return if (descriptionStringBuilder.isNotEmpty()) {
            descriptionStringBuilder.toString().trimEnd()
        } else {
            null
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapters = response.asJsoup().select("ul.chapter-list > li")

        return chapters.map {
            SChapter.create().apply {
                this.name = it.select("span.val").first()!!.ownText()
                this.url = it.select("a").first()!!.attr("href")
                this.chapter_number = this.url.split("/").last().replace(Regex("[^0-9.]"), "").toFloat()

                val calculatedDate = parseDate(it.select("span.date").first()!!.ownText())

                if (calculatedDate != null) {
                    this.date_upload = calculatedDate
                }
            }
        }
    }

    /**
     * Extension function for Calendar, that allows for an easy manipulation of a calendar instance
     */
    private fun Calendar.setWithDefaults(
        year: Int = this.get(Calendar.YEAR),
        month: Int = this.get(Calendar.MONTH),
        date: Int = this.get(Calendar.DATE),
        hourOfDay: Int = this.get(Calendar.HOUR_OF_DAY),
        minute: Int = this.get(Calendar.MINUTE),
        second: Int = this.get(Calendar.SECOND),
    ) {
        this.set(Calendar.MILLISECOND, 0)
        this.set(year, month, date, hourOfDay, minute, second)
    }

    private val regexFirstNumberPattern = Regex("^\\d*")
    private val regexLastWordPattern = Regex("\\w*\$")

    /**
     * Chapter "dates" are given by the website not as a date, but as how many seconds, minutes,
     * days, months, years ago. This leads to a lot of inaccuracy, but it's the best we have.
     */
    private fun parseDate(inputString: String): Long? {
        val timeDifference = regexFirstNumberPattern.find(inputString)?.let {
            it.value.toInt() * (-1)
        }

        val lastWord = regexLastWordPattern.find(inputString)?.value

        if (lastWord != null && timeDifference != null) {
            val calculatedTime = Calendar.getInstance()

            when (lastWord) {
                "Years", "Year" -> {
                    calculatedTime
                        .setWithDefaults(month = 0, date = 1, hourOfDay = 0, minute = 0, second = 0)
                    calculatedTime.add(Calendar.YEAR, timeDifference)
                }

                "Months", "Month" -> {
                    calculatedTime.setWithDefaults(date = 1, hourOfDay = 0, minute = 0, second = 0)
                    calculatedTime.add(Calendar.MONTH, timeDifference)
                }

                "Weeks", "Week" -> {
                    calculatedTime.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                    calculatedTime.setWithDefaults(hourOfDay = 0, minute = 0, second = 0)
                    calculatedTime.add(Calendar.WEEK_OF_YEAR, timeDifference)
                }
                "Days", "Day" -> {
                    calculatedTime.setWithDefaults(hourOfDay = 0, minute = 0, second = 0)
                    calculatedTime.add(Calendar.DATE, timeDifference)
                }
                "Hours", "Hour" -> {
                    calculatedTime.setWithDefaults(minute = 0, second = 0)
                    calculatedTime.add(Calendar.HOUR_OF_DAY, timeDifference)
                }
                "Minutes", "Minute" -> {
                    calculatedTime.setWithDefaults(second = 0)
                    calculatedTime.add(Calendar.MINUTE, timeDifference)
                }
                "Seconds", "Second" -> {
                    calculatedTime.set(Calendar.MILLISECOND, 0)
                    calculatedTime.add(Calendar.SECOND, timeDifference)
                }
            }

            return calculatedTime.time.time
        } else {
            return null
        }
    }

    private val allPagesURLPart = "/all-pages"

    override fun pageListRequest(chapter: SChapter): Request {
        return GET(chapter.url + allPagesURLPart)
    }

    private val imgSelector = "div.content-inner.inner-page > div > img.img-responsive"

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        var pageIndex = 0

        return document.select(imgSelector)
            .map { Page(pageIndex++, it.attr("src"), it.attr("src")) }
    }

    override fun fetchImageUrl(page: Page) = throw UnsupportedOperationException("Not used.")

    override fun imageUrlParse(response: Response) =
        throw UnsupportedOperationException("Not used.")

    // search
    /**
     * The search functionality of the website is uses javascript to talk to an underlying API.
     * The here implemented search function skips the javascript and talks directly with the API.
     */
    private val underlyingSearchMangaPath = "/service/advanced_search"

    /**
     * The search API won't respond properly unless a certain header field is added to each request.
     * This function prepares the searchHeader by appending the header field to the default headers.
     */
    private val searchHeaders: Headers = headers.newBuilder()
        .set("X-Requested-With", "XMLHttpRequest")
        .build()

    /**
     * All search payload parameters must be sent with each request. This ensures that even if
     * filters don't want to provide a payload parameter, no parameter will be missed.
     */
    private val defaultSearchParameter = linkedMapOf(
        Pair("type", "all"),
        Pair("manga-name", ""),
        Pair("author-name", ""),
        Pair("artist-name", ""),
        Pair("status", "both"),
    )

    /**
     * Search requests are made with POST requests to the search API of the website.
     * Filters are first given the opportunity to overwrite the default search payload values,
     * before the request body is constructed.
     * GenreFilter form an exception, since they don't have default values, instead they are just
     * added if they exist, or ignored if they don't exist.
     */
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val currentSearchParameter = LinkedHashMap(defaultSearchParameter)

        var potentialGenreGroupFilter: GenreGroupFilterManager.GenreGroupFilter? = null

        filters.forEach {
            if (it is FormBodyFilter) it.addToFormParameters(currentSearchParameter)
            if (it is GenreGroupFilterManager.GenreGroupFilter) potentialGenreGroupFilter = it
        }

        if (query.isNotEmpty()) {
            currentSearchParameter["manga-name"] = query
        }

        val requestBodyBuilder = FormBody.Builder(Charset.forName("utf8"))

        currentSearchParameter.entries.forEach {
            requestBodyBuilder.add(it.key, it.value)
            if (it.key == "artist-name") {
                potentialGenreGroupFilter?.run {
                    addToRequestPayload(requestBodyBuilder)
                }
            }
        }

        return POST(
            baseUrl + underlyingSearchMangaPath,
            searchHeaders,
            requestBodyBuilder.build(),
        )
    }

    private val searchResultSelector = "div.row"

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        return MangasPage(
            document.select(searchResultSelector).map {
                mangaFromMangaListElement(it)
            },
            false,
        )
    }

    // filters
    private val genreManager = GenreGroupFilterManager(client, baseUrl)

    override fun getFilterList() = FilterList(
        TypeFilter(),
        AuthorTextFilter(),
        ArtistTextFilter(),
        StatusFilter(),
        genreManager.getGenreGroupFilterOrPlaceholder(),
    )

    private class TypeFilter : FormBodySelectFilter(
        "Type",
        "type",
        arrayOf(
            Pair("japanese", "Japanese Manga"),
            Pair("korean", "Korean Manhwa"),
            Pair("chinese", "Chinese Manhua"),
            Pair("all", "All"),
        ),
        3,
    )

    private class AuthorTextFilter : Filter.Text("Author"), FormBodyFilter {
        override fun addToFormParameters(formParameters: MutableMap<String, String>) {
            formParameters["author-name"] = state
        }
    }

    private class ArtistTextFilter : Filter.Text("Artist"), FormBodyFilter {
        override fun addToFormParameters(formParameters: MutableMap<String, String>) {
            formParameters["artist-name"] = state
        }
    }

    private class StatusFilter : FormBodySelectFilter(
        "Status",
        "status",
        arrayOf(
            Pair("ongoing", "Ongoing"),
            Pair("completed", "Completed"),
            Pair("both", "Both"),
        ),
        2,
    )

    /**
     * GenreFilter aren't hard coded into this extension, instead it relies on asynchronous-fetching
     * of Genre information from the advanced search page of the MangaDoom website.
     * GenreFilter have to be fetched asynchronous, otherwise it would lead to a
     * NetworkOnMainThreadException. In case Genre information isn't available at the time where
     * the filters are created, a substitute Filter object is returned and a new website request is
     * made.
     */
    private class GenreGroupFilterManager(val client: OkHttpClient, val baseUrl: String) {

        fun getGenreGroupFilterOrPlaceholder(): Filter<*> {
            return when (val potentialGenreGroup = callForGenreGroup()) {
                null -> GenreNotAvailable()
                else -> potentialGenreGroup
            }
        }

        private class GenreNotAvailable :
            Filter.Header("Reset for genre filter")

        private class GenreFilter(val payloadParam: String, displayName: String) :
            Filter.CheckBox(displayName)

        class GenreGroupFilter(generatedGenreList: List<GenreFilter>) :
            Filter.Group<GenreFilter>("Genres", generatedGenreList) {
            fun addToRequestPayload(formBodyBuilder: FormBody.Builder) {
                state.filter { it.state }
                    .forEach { formBodyBuilder.add("include[]", it.payloadParam) }
            }
        }

        private var genreFiltersContent: List<Pair<String, String>>? = null
        private var genreFilterContentFrom: Long? = null

        /**
         * Checks if an object (e.g. cached response) isn't older than 15 minutes, by comparing its
         * timestamp with the current time
         */
        private fun contentUpToDate(compareTimestamp: Long?): Boolean =
            (
                compareTimestamp != null &&
                    (System.currentTimeMillis() - compareTimestamp < 15 * 60 * 1000)
                )

        /**
         * Used to generate a GenreGroupFilter from cached Pair objects or (if the cached pairs are
         * unavailable) resorts a fetch approach.
         */
        private fun callForGenreGroup(): GenreGroupFilter? {
            fun genreContentListToGenreGroup(genreFiltersContent: List<Pair<String, String>>) =
                GenreGroupFilter(
                    genreFiltersContent.map { singleGenreContent ->
                        GenreFilter(singleGenreContent.first, singleGenreContent.second)
                    },
                )

            val genreGroupFromVar = genreFiltersContent?.let { genreList ->
                genreContentListToGenreGroup(genreList)
            }

            return if (genreGroupFromVar != null && contentUpToDate(genreFilterContentFrom)) {
                genreGroupFromVar
            } else {
                generateFilterContent()?.let {
                    genreContentListToGenreGroup(it)
                }
            }
        }

        private val advancedSearchPagePath = "/advanced-search"

        /**
         * The fetch approach. Attempts to construct genre pairs from a cached response or starts a
         * new asynchronous web request.
         */
        private fun generateFilterContent(): List<Pair<String, String>>? {
            fun responseToGenreFilterContentPair(genreResponse: Response): List<Pair<String, String>> {
                val document = genreResponse.asJsoup()

                return document.select("ul.manga-cat > li").map {
                    Pair(
                        it.select("span.fa").first()!!.attr("data-id"),
                        it.ownText(),
                    )
                }
            }

            val genreResponse = client
                .newCall(
                    GET(
                        url = baseUrl + advancedSearchPagePath,
                        cache = CacheControl.FORCE_CACHE,
                    ),
                ).execute()

            return if (genreResponse.code == 200 &&
                contentUpToDate(genreResponse.receivedResponseAtMillis)
            ) {
                responseToGenreFilterContentPair(genreResponse)
            } else {
                client.newCall(
                    GET(
                        url = baseUrl + advancedSearchPagePath,
                        cache = CacheControl.FORCE_NETWORK,
                    ),
                ).enqueue(
                    object : Callback {
                        override fun onFailure(call: Call, e: IOException) = e.printStackTrace()
                        override fun onResponse(call: Call, response: Response) {
                            genreFilterContentFrom = response.receivedResponseAtMillis
                            genreFiltersContent = responseToGenreFilterContentPair(response)
                        }
                    },
                )
                null
            }
        }
    }

    /**
     * Used to create a select filter. Each entry has a name and a display name.
     */
    private open class FormBodySelectFilter(
        displayName: String,
        val payloadParam: String,
        val vals: Array<Pair<String, String>>,
        defaultValue: Int = 0,
    ) :
        Filter.Select<String>(
            displayName,
            vals.map { it.second }.toTypedArray(),
            defaultValue,
        ),
        FormBodyFilter {
        override fun addToFormParameters(formParameters: MutableMap<String, String>) {
            formParameters[payloadParam] = vals[state].first
        }
    }

    /**
     * Implemented by filters that are capable of to modifying a payload parameter.
     */
    private interface FormBodyFilter {
        fun addToFormParameters(formParameters: MutableMap<String, String>)
    }

    // common
    /**
     * The last step for parsing popular manga and search results (from jsoup element to [SManga]
     */
    private fun mangaFromMangaListElement(mangaListElement: Element): SManga {
        val titleElement = mangaListElement.select("div.col-md-4 > a").first()!!
        return mangaFromMangaTitleElement(titleElement)
    }

    /**
     * Used for latest, popular and search manga parsing to create [SManga] objects
     */
    private fun mangaFromMangaTitleElement(mangaTitleElement: Element): SManga = SManga.create()
        .apply {
            this.title = mangaTitleElement.attr("title")
            this.setUrlWithoutDomain(mangaTitleElement.attr("href"))
            this.thumbnail_url = mangaTitleElement.select("img").first()!!
                .attr("src")
        }
}
