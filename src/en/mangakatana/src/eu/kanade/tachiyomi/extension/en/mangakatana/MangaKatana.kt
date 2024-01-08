package eu.kanade.tachiyomi.extension.en.mangakatana

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.SimpleDateFormat
import java.util.Locale

class MangaKatana : ConfigurableSource, ParsedHttpSource() {
    override val name = "MangaKatana"

    override val baseUrl = "https://mangakatana.com"

    override val lang = "en"

    override val supportsLatest = true

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }
    private val serverPreference = "SERVER_PREFERENCE"

    override val client: OkHttpClient = network.cloudflareClient.newBuilder().addNetworkInterceptor { chain ->
        val originalResponse = chain.proceed(chain.request())
        if (originalResponse.headers("Content-Type").contains("application/octet-stream")) {
            val orgBody = originalResponse.body.bytes()
            val extension = chain.request().url.toString().substringAfterLast(".")
            val newBody = orgBody.toResponseBody("image/$extension".toMediaTypeOrNull())
            originalResponse.newBuilder()
                .body(newBody)
                .build()
        } else {
            originalResponse
        }
    }.build()

    // Latest

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/page/$page", headers)

    override fun latestUpdatesSelector() = "div#book_list > div.item"

    override fun latestUpdatesFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.selectFirst("div.text > h3 > a")!!.attr("href"))
        title = element.selectFirst("div.text > h3 > a")!!.ownText()
        thumbnail_url = element.selectFirst("img")!!.attr("abs:src")
    }

    override fun latestUpdatesNextPageSelector() = "a.next.page-numbers"

    // Popular (is actually alphabetical)

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/manga/page/$page", headers)

    override fun popularMangaSelector() = latestUpdatesSelector()

    override fun popularMangaFromElement(element: Element) = latestUpdatesFromElement(element)

    override fun popularMangaNextPageSelector() = latestUpdatesNextPageSelector()

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val filterList = if (filters.isEmpty()) getFilterList() else filters

        if (query.isNotEmpty()) {
            val type = filterList.find { it is TypeFilter } as TypeFilter
            val url = "$baseUrl/page/$page".toHttpUrlOrNull()!!.newBuilder()
                .addQueryParameter("search", query)
                .addQueryParameter("search_by", type.toUriPart())
            return GET(url.toString(), headers)
        } else {
            val url = "$baseUrl/manga/page/$page".toHttpUrlOrNull()!!.newBuilder()
                .addQueryParameter("filter", "1")
            for (filter in filterList) {
                when (filter) {
                    is GenreList -> {
                        val includedGenres = mutableListOf<String>()
                        val excludedGenres = mutableListOf<String>()
                        filter.state.forEach {
                            if (it.isIncluded()) {
                                includedGenres.add(it.id)
                            } else if (it.isExcluded()) {
                                excludedGenres.add(it.id)
                            }
                        }
                        if (includedGenres.isNotEmpty()) url.addQueryParameter("include", includedGenres.joinToString("_"))
                        if (excludedGenres.isNotEmpty()) url.addQueryParameter("exclude", excludedGenres.joinToString("_"))
                    }
                    is GenreInclusionMode -> url.addQueryParameter("include_mode", filter.toUriPart())
                    is SortFilter -> url.addQueryParameter("order", filter.toUriPart())
                    is StatusFilter -> {
                        if (filter.toUriPart().isNotEmpty()) {
                            url.addQueryParameter("status", filter.toUriPart())
                        }
                    }
                    is ChaptersFilter -> {
                        when (filter.state.trim()) {
                            "-1" -> url.addQueryParameter("chapters", "e1")
                            "" -> url.addQueryParameter("chapters", "1")
                            else -> url.addQueryParameter("chapters", filter.state.trim())
                        }
                    }
                    else -> {}
                }
            }
            return GET(url.toString(), headers)
        }
    }

    override fun searchMangaSelector() = latestUpdatesSelector()

    override fun searchMangaFromElement(element: Element) = latestUpdatesFromElement(element)

    override fun searchMangaNextPageSelector() = latestUpdatesNextPageSelector()

    override fun searchMangaParse(response: Response): MangasPage {
        // If search request redirects to a single manga page, use alternative parsing
        val pathSegments = response.request.url.pathSegments
        return if (pathSegments[0] == "manga" && pathSegments[1] != "page") {
            val document = response.asJsoup()
            val manga = SManga.create().apply {
                thumbnail_url = parseThumbnail(document)
                title = document.select("h1.heading").first()!!.text()
            }
            manga.setUrlWithoutDomain(response.request.url.toString())
            MangasPage(listOf(manga), false)
        } else {
            super.searchMangaParse(response)
        }
    }

    // Details

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        author = document.select(".author").eachText().joinToString()
        description = document.select(".summary > p").text() +
            (document.select(".alt_name").text().takeIf { it.isNotBlank() }?.let { "\n\nAlt name(s): $it" } ?: "")
        status = parseStatus(document.select(".value.status").text())
        genre = document.select(".genres > a").joinToString { it.text() }
        thumbnail_url = parseThumbnail(document)
    }

    private fun parseThumbnail(document: Document) = document.select("div.media div.cover img").attr("abs:src")

    private fun parseStatus(status: String) = when {
        status.contains("Ongoing") -> SManga.ONGOING
        status.contains("Completed") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // Chapters

    override fun chapterListSelector() = "tr:has(.chapter)"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.select("a").attr("href"))
        name = element.select("a").text()
        date_upload = dateFormat.parse(element.select(".update_time").text())?.time ?: 0
    }

    private val imageArrayNameRegex = Regex("""data-src['"],\s*(\w+)""")
    private val imageUrlRegex = Regex("""'([^']*)'""")

    // Page List

    override fun pageListRequest(chapter: SChapter): Request {
        val serverSuffix = preferences.getString(serverPreference, "")?.takeIf { it.isNotBlank() }?.let { "?sv=$it" } ?: ""
        return GET(baseUrl + chapter.url + serverSuffix, headers)
    }

    override fun pageListParse(document: Document): List<Page> {
        val imageScript = document.select("script:containsData(data-src)").firstOrNull()?.data()
            ?: return emptyList()
        val imageArrayName = imageArrayNameRegex.find(imageScript)?.groupValues?.get(1)
            ?: return emptyList()
        val imageArrayRegex = Regex("""var $imageArrayName=\[([^\[]*)]""")

        return imageArrayRegex.find(imageScript)?.groupValues?.get(1)?.let {
            imageUrlRegex.findAll(it).asIterable().mapIndexed { i, mr ->
                Page(i, "", mr.groupValues[1])
            }
        } ?: emptyList()
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not Used")

    // Preferences

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val serverPref = ListPreference(screen.context).apply {
            key = "server_preference"
            title = "Server preference"
            entries = arrayOf("Server 1", "Server 2", "Server 3")
            entryValues = arrayOf("", "mk", "3")
            setDefaultValue("")
            summary = "%s"

            setOnPreferenceChangeListener { _, newValue ->
                val selected = newValue.toString()
                preferences.edit().putString(serverPreference, selected).commit()
            }
        }

        screen.addPreference(serverPref)
    }

    // Filters

    override fun getFilterList() = FilterList(
        // MangaKarate does not support genre filtering and text search at the same time
        Filter.Header("NOTE: Other filters ignored if using text search!"),
        TypeFilter(),
        Filter.Separator(),
        GenreList(genres),
        GenreInclusionMode(),
        SortFilter(),
        StatusFilter(),
        Filter.Separator(),
        Filter.Header("Input -1 to search for only oneshots"),
        ChaptersFilter(),
    )

    private class TypeFilter : UriPartFilter(
        "Text search by",
        arrayOf(
            Pair("Title", "book_name"),
            Pair("Author", "author"),
        ),
    )

    private class Genre(val id: String, name: String) : Filter.TriState(name)
    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Genres", genres)
    private val genres = listOf(
        Genre("4-koma", "4 koma"),
        Genre("action", "Action"),
        Genre("adult", "Adult"),
        Genre("adventure", "Adventure"),
        Genre("artbook", "Artbook"),
        Genre("award-winning", "Award winning"),
        Genre("comedy", "Comedy"),
        Genre("cooking", "Cooking"),
        Genre("doujinshi", "Doujinshi"),
        Genre("drama", "Drama"),
        Genre("ecchi", "Ecchi"),
        Genre("erotica", "Erotica"),
        Genre("fantasy", "Fantasy"),
        Genre("gender-bender", "Gender Bender"),
        Genre("gore", "Gore"),
        Genre("harem", "Harem"),
        Genre("historical", "Historical"),
        Genre("horror", "Horror"),
        Genre("isekai", "Isekai"),
        Genre("josei", "Josei"),
        Genre("loli", "Loli"),
        Genre("manhua", "Manhua"),
        Genre("manhwa", "Manhwa"),
        Genre("martial-arts", "Martial Arts"),
        Genre("mecha", "Mecha"),
        Genre("medical", "Medical"),
        Genre("music", "Music"),
        Genre("mystery", "Mystery"),
        Genre("one-shot", "One shot"),
        Genre("overpowered-mc", "Overpowered MC"),
        Genre("psychological", "Psychological"),
        Genre("reincarnation", "Reincarnation"),
        Genre("romance", "Romance"),
        Genre("school-life", "School Life"),
        Genre("sci-fi", "Sci-fi"),
        Genre("seinen", "Seinen"),
        Genre("sexual-violence", "Sexual violence"),
        Genre("shota", "Shota"),
        Genre("shoujo", "Shoujo"),
        Genre("shoujo-ai", "Shoujo Ai"),
        Genre("shounen", "Shounen"),
        Genre("shounen-ai", "Shounen Ai"),
        Genre("slice-of-life", "Slice of Life"),
        Genre("sports", "Sports"),
        Genre("super-power", "Super power"),
        Genre("supernatural", "Supernatural"),
        Genre("survival", "Survival"),
        Genre("time-travel", "Time Travel"),
        Genre("tragedy", "Tragedy"),
        Genre("webtoon", "Webtoon"),
        Genre("yaoi", "Yaoi"),
        Genre("yuri", "Yuri"),
    )

    private class GenreInclusionMode : UriPartFilter(
        "Genre inclusion mode",
        arrayOf(
            Pair("And", "and"),
            Pair("Or", "or"),
        ),
    )

    private class ChaptersFilter : Filter.Text("Minimum Chapters")

    private class SortFilter : UriPartFilter(
        "Sort by",
        arrayOf(
            Pair("Latest update", "latest"),
            Pair("New manga", "new"),
            Pair("A-Z", "az"),
            Pair("Number of chapters", "numc"),
        ),
    )

    private class StatusFilter : UriPartFilter(
        "Status",
        arrayOf(
            Pair("All", ""),
            Pair("Cancelled", "0"),
            Pair("Ongoing", "1"),
            Pair("Completed", "2"),
        ),
    )

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    companion object {
        val dateFormat by lazy {
            SimpleDateFormat("MMM-dd-yyyy", Locale.US)
        }
    }
}
