package eu.kanade.tachiyomi.extension.en.mangaowl

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.extension.en.mangaowl.MangaOwlFactory.Genre
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.text.DateFormat
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class MangaOwl(
    private val collectionUrl: String,
    extraName: String = "",
    private val genresList: List<Genre> = listOf(),
) : ConfigurableSource, ParsedHttpSource() {
    override val name: String = "MangaOwl $extraName"
    override val lang = "en"
    override val supportsLatest = true

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    private val defaultDomain: String = getMirrorPref()!!
    override val baseUrl = "https://$defaultDomain"
    private val API = "https://api.$defaultDomain/v1"
    private val searchPath = "search"

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val mirrorPref = ListPreference(screen.context).apply {
            key = MIRROR_PREF_KEY
            title = MIRROR_PREF_TITLE
            entries = MIRROR_PREF_ENTRIES
            entryValues = MIRROR_PREF_ENTRY_VALUES
            setDefaultValue(MIRROR_PREF_DEFAULT_VALUE)
            summary = "Current: %s\n(Restart app to apply new setting.)"

            setOnPreferenceChangeListener { _, newValue ->
                val selected = newValue as String
                val index = findIndexOfValue(selected)
                val entry = entryValues[index] as String
                preferences.edit().putString(MIRROR_PREF_KEY, entry).commit()
            }
        }
        screen.addPreference(mirrorPref)
    }

    private fun getMirrorPref(): String? = preferences.getString(MIRROR_PREF_KEY, MIRROR_PREF_DEFAULT_VALUE)

    private val json: Json by injectLazy()

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(1, TimeUnit.MINUTES)
        .readTimeout(1, TimeUnit.MINUTES)
        .writeTimeout(1, TimeUnit.MINUTES)
        .build()

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/$collectionUrl?ordering=-view_count&page=$page", headers)
    }

    override fun popularMangaSelector() = "div.manga-item"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("a:nth-child(2)").let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.text()
        }
        manga.thumbnail_url = element.select("a:nth-child(1) > img").attr("src")

        return manga
    }

    override fun popularMangaNextPageSelector() = "div.pagination > a[title='Next']"

    // Latest

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/$collectionUrl?ordering=-modified_at&page=$page", headers)
    }

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return if (query.isNotEmpty() || filters.isEmpty()) {
            // Search won't work together with filter
            GET("$baseUrl/$searchPath?q=$query&page=$page", headers)
        } else {
            val url = "$baseUrl/$collectionUrl".toHttpUrl().newBuilder()
            filters.forEach { filter ->
                when (filter) {
                    is SortFilter -> if (!filter.toUriPart().isNullOrEmpty()) {
                        url.addQueryParameter("ordering", filter.toUriPart())
                    }
                    is StatusFilter -> if (!filter.toUriPart().isNullOrEmpty()) {
                        url.addQueryParameter("status", filter.toUriPart())
                    }
                    is GenreFilter ->
                        filter.state
                            .filter { it.state }
                            .forEach { url.addQueryParameter("genres", it.uriPart) }
                    else -> {}
                }
            }

            url.apply {
                addQueryParameter("page", page.toString())
            }
            GET(url.toString(), headers)
        }
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // Manga summary page

    override fun mangaDetailsRequest(manga: SManga): Request {
        val slug = manga.url.substringAfterLast("/")
        return GET("$API/stories/$slug", headers)
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val jsonStr = document.body().text()
        val story = json.parseToJsonElement(jsonStr).jsonObject

        return SManga.create().apply {
            title = story.jsonObject["name"]?.jsonPrimitive?.content.toString()
            thumbnail_url = story.jsonObject["thumbnail"]?.jsonPrimitive?.content.toString()
            author = story.jsonObject["authors"]?.jsonArray?.map {
                it.jsonObject["name"]?.jsonPrimitive?.content
            }?.joinToString(", ")
            status = story.jsonObject["status"]?.jsonPrimitive?.content?.let {
                when {
                    it.contains("ongoing") -> SManga.ONGOING
                    it.contains("completed") -> SManga.COMPLETED
                    else -> SManga.UNKNOWN
                }
            } ?: SManga.UNKNOWN
            genre = story.jsonObject["genres"]?.jsonArray?.map {
                it.jsonObject["name"]?.jsonPrimitive?.content
            }?.joinToString(", ")

            val description = story.jsonObject["description"]?.jsonPrimitive?.contentOrNull ?: ""
            val altName = (story.jsonObject["al_name"]?.jsonPrimitive?.contentOrNull ?: "")
                .replace("Alternative : ", "")
            val rating = story.jsonObject["rating"]?.jsonPrimitive?.float
            val viewCount = story.jsonObject["view_count"]?.jsonPrimitive?.int
            this.description = "$description\n\n" +
                (if (altName.isNotEmpty()) "Alternative names: $altName\n\n" else "") +
                "Rating: $rating\nViews: $viewCount"
        }
    }

    // Chapters

    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.US)
        val jsonStr = response.body.string()
        val story = json.parseToJsonElement(jsonStr).jsonObject
        val slug = story.jsonObject["slug"]?.jsonPrimitive?.content.toString()

        return story.jsonObject["chapters"]?.jsonArray?.map {
            val id = it.jsonObject["id"]?.jsonPrimitive?.int
            SChapter.create().apply {
                url = "$baseUrl/10-reading/$slug/$id"
                name = it.jsonObject["name"]?.jsonPrimitive?.content.toString()
                date_upload = dateFormat.tryParse(it.jsonObject["created_at"]?.jsonPrimitive?.content.toString())
            }
        }!!.reversed()
    }

    override fun chapterListSelector() = throw UnsupportedOperationException()
    override fun chapterFromElement(element: Element): SChapter = throw UnsupportedOperationException()

    private fun DateFormat.tryParse(str: String): Long = try {
        parse(str)!!.time
    } catch (_: ParseException) {
        0L
    }

    // Pages
    override fun pageListRequest(chapter: SChapter): Request {
        val id = chapter.url.substringAfterLast("/")
        return GET("$API/chapters/$id/images?page_size=1000", headers)
    }

    override fun pageListParse(document: Document): List<Page> {
        val jsonStr = document.body().text()
        val pages = json.parseToJsonElement(jsonStr).jsonObject

        return pages.jsonObject["results"]?.jsonArray?.mapIndexed { idx, obj ->
            Page(
                index = idx,
                imageUrl = obj.jsonObject["image"]?.jsonPrimitive?.content,
            )
        }!!
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()

    // Filters

    override fun getFilterList() = FilterList(
        Filter.Header("Search query won't use filters"),
        GenreFilter(genresList),
        StatusFilter(),
        SortFilter(),
    )

    private class SortFilter : UriPartFilter(
        "Sort by",
        arrayOf(
            Pair("Default", null),
            Pair("Most view", "-view_count"),
            Pair("Added", "created_at"),
            Pair("Last update", "-modified_at"),
            Pair("High rating", "rating"),
        ),
    )

    private class StatusFilter : UriPartFilter(
        "Status",
        arrayOf(
            Pair("Any", null),
            Pair("Completed", "completed"),
            Pair("Ongoing", "ongoing"),
        ),
    )

    private class GenreFilter(genres: List<Genre>) : Filter.Group<Genre>("Genres", genres)

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String?>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    companion object {
        private const val MIRROR_PREF_KEY = "MIRROR"
        private const val MIRROR_PREF_TITLE = "Mirror"
        private val MIRROR_PREF_ENTRIES = arrayOf(
            "mangaowl.to",
            "mangabuddy.to",
            "mangafreak.to",
            "toonily.to",
            "manganato.so",
            // "mangakakalot.so", This one doesn't have its own API
        )
        private val MIRROR_PREF_ENTRY_VALUES = MIRROR_PREF_ENTRIES
        private val MIRROR_PREF_DEFAULT_VALUE = MIRROR_PREF_ENTRY_VALUES[0]
    }
}
