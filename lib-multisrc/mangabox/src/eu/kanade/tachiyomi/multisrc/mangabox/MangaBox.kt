package eu.kanade.tachiyomi.multisrc.mangabox

import android.annotation.SuppressLint
import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.tryParse
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.IOException
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.regex.Pattern

abstract class MangaBox(
    override val name: String,
    private val mirrorEntries: Array<String>,
    override val lang: String,
    private val dateFormat: SimpleDateFormat = SimpleDateFormat(
        "MMM-dd-yyyy HH:mm",
        Locale.ENGLISH,
    ).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    },
) : ParsedHttpSource(), ConfigurableSource {

    override val supportsLatest = true

    override val baseUrl: String get() = mirror

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .addInterceptor(::useAltCdnInterceptor)
        .build()

    private fun SharedPreferences.getMirrorPref(): String =
        getString(PREF_USE_MIRROR, mirrorEntries[0])!!

    private val preferences: SharedPreferences by getPreferencesLazy {
        // if current mirror is not in mirrorEntries, set default
        if (getMirrorPref() !in mirrorEntries.map { "${URL_PREFIX}$it" }) {
            edit().putString(PREF_USE_MIRROR, "${URL_PREFIX}${mirrorEntries[0]}").apply()
        }
    }

    private var mirror = ""
        get() {
            if (field.isNotEmpty()) {
                return field
            }

            field = preferences.getMirrorPref()
            return field
        }

    private val cdnSet =
        MangaBoxLinkedCdnSet() // Stores all unique CDNs that the extension can use to retrieve chapter images

    private class MangaBoxFallBackTag // Custom empty class tag to use as an identifier that the specific request is fallback-able

    private fun HttpUrl.getBaseUrl(): String =
        "${URL_PREFIX}${this.host}${
        when (this.port) {
            80, 443 -> ""
            else -> ":${this.port}"
        }
        }"

    private fun useAltCdnInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (cdnSet.isEmpty()) {
            return chain.proceed(request)
        }
        val requestTag = request.tag(MangaBoxFallBackTag::class.java)
        val originalResponse: Response? = try {
            chain.proceed(request)
        } catch (e: IOException) {
            if (requestTag == null) {
                throw e
            } else {
                null
            }
        }

        if (requestTag == null || originalResponse?.isSuccessful == true) {
            requestTag?.let {
                // Move working cdn to first so it gets priority during iteration
                cdnSet.moveItemToFirst(request.url.getBaseUrl())
            }

            return originalResponse!!
        }

        // Close the original response if it's not successful
        originalResponse?.close()

        for (cdnUrl in cdnSet) {
            var tryResponse: Response? = null

            try {
                val newUrl = cdnUrl.toHttpUrl().newBuilder()
                    .encodedPath(request.url.encodedPath)
                    .fragment(request.url.fragment)
                    .build()

                // Create a new request with the updated URL
                val newRequest = request.newBuilder()
                    .url(newUrl)
                    .build()

                // Proceed with the new request
                tryResponse = chain.proceed(newRequest)

                // Check if the response is successful
                if (tryResponse.isSuccessful) {
                    // Move working cdn to first so it gets priority during iteration
                    cdnSet.moveItemToFirst(newRequest.url.getBaseUrl())

                    return tryResponse
                }

                tryResponse.close()
            } catch (_: IOException) {
                tryResponse?.close()
            }
        }

        // If all CDNs fail, throw an error
        return throw IOException("All CDN attempts failed.")
    }

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    open val popularUrlPath = "manga-list/hot-manga?page="

    open val latestUrlPath = "manga-list/latest-manga?page="

    open val simpleQueryPath = "search/story/"

    override fun popularMangaSelector() = "div.truyen-list > div.list-truyen-item-wrap, div.comic-list > .list-comic-item-wrap"

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/$popularUrlPath$page", headers)
    }

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/$latestUrlPath$page", headers)
    }

    private fun mangaFromElement(element: Element, urlSelector: String = "h3 a"): SManga {
        return SManga.create().apply {
            element.select(urlSelector).first()!!.let {
                url = it.attr("abs:href")
                    .substringAfter(baseUrl) // intentionally not using setUrlWithoutDomain
                title = it.text()
            }
            thumbnail_url = element.select("img").first()!!.attr("abs:src")
        }
    }

    override fun popularMangaFromElement(element: Element): SManga = mangaFromElement(element)

    override fun latestUpdatesFromElement(element: Element): SManga = mangaFromElement(element)

    override fun popularMangaNextPageSelector() =
        "div.group_page, div.group-page a:not([href]) + a:not(:contains(Last))"

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return if (query.isNotBlank()) {
            val url = "$baseUrl/$simpleQueryPath".toHttpUrl().newBuilder()
                .addPathSegment(normalizeSearchQuery(query))
                .addQueryParameter("page", page.toString())
                .build()

            return GET(url, headers)
        } else {
            val url = "$baseUrl/genre".toHttpUrl().newBuilder()
            url.addQueryParameter("page", page.toString())
            filters.forEach { filter ->
                when (filter) {
                    is SortFilter -> url.addQueryParameter("type", filter.toUriPart())
                    is StatusFilter -> url.addQueryParameter("state", filter.toUriPart())
                    is GenreFilter -> url.addPathSegment(filter.toUriPart()!!)
                    else -> {}
                }
            }

            GET(url.build(), headers)
        }
    }

    override fun searchMangaSelector() = ".panel_story_list .story_item, div.list-truyen-item-wrap, div.list-comic-item-wrap"

    override fun searchMangaFromElement(element: Element) = mangaFromElement(element)

    override fun searchMangaNextPageSelector() =
        "a.page_select + a:not(.page_last), a.page-select + a:not(.page-last)"

    open val mangaDetailsMainSelector = "div.manga-info-top, div.panel-story-info"

    open val thumbnailSelector = "div.manga-info-pic img, span.info-image img"

    open val descriptionSelector = "div#noidungm, div#panel-story-info-description, div#contentBox"

    override fun mangaDetailsRequest(manga: SManga): Request {
        if (manga.url.startsWith("http")) {
            return GET(manga.url, headers)
        }
        return super.mangaDetailsRequest(manga)
    }

    private fun checkForRedirectMessage(document: Document) {
        if (document.select("body").text().startsWith("REDIRECT :")) {
            throw Exception("Source URL has changed")
        }
    }

    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            document.select(mangaDetailsMainSelector).firstOrNull()?.let { infoElement ->
                title = infoElement.select("h1, h2").first()!!.text()
                author = infoElement.select("li:contains(author) a, td:containsOwn(author) + td a")
                    .eachText().joinToString()
                status = parseStatus(
                    infoElement.select("li:contains(status), td:containsOwn(status) + td").text(),
                )
                genre = infoElement.select("div.manga-info-top li:contains(genres)").firstOrNull()
                    ?.select("a")?.joinToString { it.text() } // kakalot
                    ?: infoElement.select("td:containsOwn(genres) + td a")
                        .joinToString { it.text() } // nelo
            } ?: checkForRedirectMessage(document)
            description = document.select(descriptionSelector).firstOrNull()?.ownText()
                ?.replace("""^$title summary:\s""".toRegex(), "")
                ?.replace("""<\s*br\s*/?>""".toRegex(), "\n")
                ?.replace("<[^>]*>".toRegex(), "")
            thumbnail_url = document.select(thumbnailSelector).attr("abs:src")

            // add alternative name to manga description
            document.select(altNameSelector).firstOrNull()?.ownText()?.let {
                if (it.isBlank().not()) {
                    description = when {
                        description.isNullOrBlank() -> altName + it
                        else -> description + "\n\n$altName" + it
                    }
                }
            }
        }
    }

    open val altNameSelector = ".story-alternative, tr:has(.info-alternative) h2"
    open val altName = "Alternative Name" + ": "

    private fun parseStatus(status: String?) = when {
        status == null -> SManga.UNKNOWN
        status.contains("Ongoing") -> SManga.ONGOING
        status.contains("Completed") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListRequest(manga: SManga): Request {
        if (manga.url.startsWith("http")) {
            return GET(manga.url, headers)
        }
        return super.chapterListRequest(manga)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        return document.select(chapterListSelector())
            .map { chapterFromElement(it) }
            .also { if (it.isEmpty()) checkForRedirectMessage(document) }
    }

    override fun chapterListSelector() = "div.chapter-list div.row, ul.row-content-chapter li"

    protected open val alternateChapterDateSelector = String()

    private fun Element.selectDateFromElement(): Element {
        val defaultChapterDateSelector = "span"
        return this.select(defaultChapterDateSelector).lastOrNull() ?: this.select(
            alternateChapterDateSelector,
        ).last()!!
    }

    override fun chapterFromElement(element: Element): SChapter {
        return SChapter.create().apply {
            element.select("a").let {
                url = it.attr("abs:href")
                    .substringAfter(baseUrl) // intentionally not using setUrlWithoutDomain
                name = it.text()
                scanlator =
                    it.attr("abs:href").toHttpUrl().host // show where chapters are actually from
            }
            date_upload = dateFormat.tryParse(element.selectDateFromElement().attr("title"))
        }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        if (chapter.url.startsWith("http")) {
            return GET(chapter.url, headers)
        }
        return super.pageListRequest(chapter)
    }

    private fun extractArray(scriptContent: String, arrayName: String): List<String> {
        val pattern = Pattern.compile("$arrayName\\s*=\\s*\\[([^]]+)]")
        val matcher = pattern.matcher(scriptContent)
        val arrayValues = mutableListOf<String>()

        if (matcher.find()) {
            val arrayContent = matcher.group(1)
            val values = arrayContent?.split(",")
            if (values != null) {
                for (value in values) {
                    arrayValues.add(
                        value.trim()
                            .removeSurrounding("\"")
                            .replace("\\/", "/")
                            .removeSuffix("/"),
                    )
                }
            }
        }

        return arrayValues
    }

    override fun pageListParse(document: Document): List<Page> {
        val content = document.select("script:containsData(cdns =)").joinToString("\n") { it.data() }
        val cdns =
            extractArray(content, "cdns") + extractArray(content, "backupImage")
        val chapterImages = extractArray(content, "chapterImages")

        // Add all parsed cdns to set
        cdnSet.addAll(cdns)

        return chapterImages.mapIndexed { i, imagePath ->
            val parsedUrl = cdns[0].toHttpUrl().run {
                newBuilder()
                    .encodedPath(
                        "/$imagePath".replace(
                            "//",
                            "/",
                        ),
                    ) // replace ensures that there's at least one trailing slash prefix
                    .build()
                    .toString()
            }

            Page(i, document.location(), parsedUrl)
        }.ifEmpty {
            document.select("div.container-chapter-reader > img").mapIndexed { i, img ->
                Page(i, imageUrl = img.absUrl("src"))
            }
        }
    }

    override fun imageRequest(page: Page): Request {
        return GET(page.imageUrl!!, headers).newBuilder()
            .tag(MangaBoxFallBackTag::class.java, MangaBoxFallBackTag()).build()
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()

    // Based on change_alias JS function from Mangakakalot's website
    @SuppressLint("DefaultLocale")
    open fun normalizeSearchQuery(query: String): String {
        var str = query.lowercase()
        str = str.replace("[àáạảãâầấậẩẫăằắặẳẵ]".toRegex(), "a")
        str = str.replace("[èéẹẻẽêềếệểễ]".toRegex(), "e")
        str = str.replace("[ìíịỉĩ]".toRegex(), "i")
        str = str.replace("[òóọỏõôồốộổỗơờớợởỡ]".toRegex(), "o")
        str = str.replace("[ùúụủũưừứựửữ]".toRegex(), "u")
        str = str.replace("[ỳýỵỷỹ]".toRegex(), "y")
        str = str.replace("đ".toRegex(), "d")
        str = str.replace(
            """!|@|%|\^|\*|\(|\)|\+|=|<|>|\?|/|,|\.|:|;|'| |"|&|#|\[|]|~|-|$|_""".toRegex(),
            "_",
        )
        str = str.replace("_+_".toRegex(), "_")
        str = str.replace("""^_+|_+$""".toRegex(), "")
        return str
    }

    override fun getFilterList() = FilterList(
        Filter.Header("NOTE: Ignored if using text search!"),
        Filter.Separator(),
        SortFilter(getSortFilters()),
        StatusFilter(getStatusFilters()),
        GenreFilter(getGenreFilters()),
    )

    private class SortFilter(vals: Array<Pair<String?, String>>) : UriPartFilter("Order by", vals)
    private class StatusFilter(vals: Array<Pair<String?, String>>) : UriPartFilter("Status", vals)
    private class GenreFilter(vals: Array<Pair<String?, String>>) : UriPartFilter("Category", vals)

    private fun getSortFilters(): Array<Pair<String?, String>> = arrayOf(
        Pair("latest", "Latest"),
        Pair("newest", "Newest"),
        Pair("topview", "Top read"),
    )

    open fun getStatusFilters(): Array<Pair<String?, String>> = arrayOf(
        Pair("all", "ALL"),
        Pair("completed", "Completed"),
        Pair("ongoing", "Ongoing"),
        Pair("drop", "Dropped"),
    )

    open fun getGenreFilters(): Array<Pair<String?, String>> = arrayOf(
        Pair("all", "ALL"),
        Pair("action", "Action"),
        Pair("adult", "Adult"),
        Pair("adventure", "Adventure"),
        Pair("comedy", "Comedy"),
        Pair("cooking", "Cooking"),
        Pair("doujinshi", "Doujinshi"),
        Pair("drama", "Drama"),
        Pair("ecchi", "Ecchi"),
        Pair("fantasy", "Fantasy"),
        Pair("gender-bender", "Gender bender"),
        Pair("harem", "Harem"),
        Pair("historical", "Historical"),
        Pair("horror", "Horror"),
        Pair("isekai", "Isekai"),
        Pair("josei", "Josei"),
        Pair("manhua", "Manhua"),
        Pair("manhwa", "Manhwa"),
        Pair("martial-arts", "Martial arts"),
        Pair("mature", "Mature"),
        Pair("mecha", "Mecha"),
        Pair("medical", "Medical"),
        Pair("mystery", "Mystery"),
        Pair("one-shot", "One shot"),
        Pair("psychological", "Psychological"),
        Pair("romance", "Romance"),
        Pair("school-life", "School life"),
        Pair("sci-fi", "Sci fi"),
        Pair("seinen", "Seinen"),
        Pair("shoujo", "Shoujo"),
        Pair("shoujo-ai", "Shoujo ai"),
        Pair("shounen", "Shounen"),
        Pair("shounen-ai", "Shounen ai"),
        Pair("slice-of-life", "Slice of life"),
        Pair("smut", "Smut"),
        Pair("sports", "Sports"),
        Pair("supernatural", "Supernatural"),
        Pair("tragedy", "Tragedy"),
        Pair("webtoons", "Webtoons"),
        Pair("yaoi", "Yaoi"),
        Pair("yuri", "Yuri"),
    )

    open class UriPartFilter(displayName: String, private val vals: Array<Pair<String?, String>>) :
        Filter.Select<String>(displayName, vals.map { it.second }.toTypedArray()) {
        fun toUriPart() = vals[state].first
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = PREF_USE_MIRROR
            title = "Mirror"
            entries = mirrorEntries
            entryValues = mirrorEntries.map { "${URL_PREFIX}$it" }.toTypedArray()
            setDefaultValue(entryValues[0])
            summary = "%s"

            setOnPreferenceChangeListener { _, newValue ->
                // Update values
                mirror = newValue as String
                true
            }
        }.let(screen::addPreference)
    }

    companion object {
        private const val PREF_USE_MIRROR = "pref_use_mirror"
        private const val URL_PREFIX = "https://"
    }
}
