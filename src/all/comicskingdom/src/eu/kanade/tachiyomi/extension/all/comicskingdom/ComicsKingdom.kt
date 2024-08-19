package eu.kanade.tachiyomi.extension.all.comicskingdom

import android.app.Application
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

class ComicsKingdom(override val lang: String) : ConfigurableSource, ParsedHttpSource() {

    private val json: Json by injectLazy()
    private val langText = if (lang == "en") "comics" else "spanish"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
    private val compactChapterCountRegex = Regex("\"totalItems\":(\\d+)")
    private val urlBase = "comicskingdom.com"

    override val name = "Comics Kingdom"

    override val baseUrl = "https://$urlBase"

    override val supportsLatest = true

    override fun popularMangaSelector() = ".comic-result-container .comic-card"
    override fun latestUpdatesSelector() = popularMangaSelector()
    override fun searchMangaSelector() =
        "h2:contains(Comic Titles) ~ div .posts-container .comic-card"

    override fun popularMangaNextPageSelector() = ".ck-pagination__item--next"
    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()
    override fun searchMangaNextPageSelector() =
        "h2:contains(Comic Titles) ~ div .ck-pagination__item--next"

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/$langText?page=$page", headers)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/$langText?page=$page&sortby=newest", headers)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
        GET(
            "$baseUrl/search".toHttpUrl().newBuilder().apply {
                addQueryParameter("key", query)
                // addQueryParameter("page", page.toString()) for singular strips
                addQueryParameter("featurepage", page.toString())
                if (!filters.isEmpty()) {
                    for (filter in filters) {
                        when (filter) {
                            is SortFilter -> {
                                addQueryParameter("sortby", filter.getSortQuery())
                            }

                            is GenreList -> {
                                // addQueryParameter("ck_genre", filter.included.joinToString(",")) for singular strips
                                addQueryParameter(
                                    "feature_genre",
                                    filter.included.joinToString(","),
                                )
                            }

                            else -> {}
                        }
                    }
                }

                if (lang == "es") {
                    addQueryParameter("feature_language", "spanish")
                    //    addQueryParameter("ck_language", "spanish")  for singular strips
                }
            }.build(),
            headers,
        )

    override fun popularMangaFromElement(element: Element): SManga =
        SManga.create().apply {
            setUrlWithoutDomain(
                element.selectFirst(".card-title__link")!!.attr("abs:href")
                    .substringBeforeLast("/"),
            )
            title = element.selectFirst(".card-title__link")!!.text()
            thumbnail_url = element.selectFirst("img")?.attr("abs:src")
        }

    override fun latestUpdatesFromElement(element: Element): SManga =
        popularMangaFromElement(element)

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        status = SManga.UNKNOWN
        author = document.selectFirst(".feature-card .card-content")?.text()
        description = document.selectFirst(".feature-about__description")?.text()
        thumbnail_url = document.selectFirst(".feature-header__media img")?.attr("abs:src")
    }


    override fun chapterListParse(response: Response): List<SChapter> {
        val mangaName = response.request.url.pathSegments.last()

        if (shouldCompact()) {
            val postCount = compactChapterCountRegex.findAll(response.body.string())
                .find { result -> result.groupValues[1].toDouble() > 0 }!!.groupValues[1].toDouble()
            val maxPage = ceil(postCount / 100) // 100 is max for the api
            return List(maxPage.roundToInt()) { idx ->
                SChapter.create().apply {
                    chapter_number = idx * 0.01F
                    name =
                        "${idx * 100 + 1}-${if (postCount - (idx + 1) * 100 < 0) postCount.toInt() else (idx + 1) * 100}"
                    url =
                        "https://wp.$urlBase/wp-json/wp/v2/ck_comic".toHttpUrl()
                            .newBuilder() // https://wp.comicskingdom.com/wp-json/wp/v2 shows all api endpoints and possible args
                            .apply {
                                addQueryParameter("per_page", 100.toString()) // 100 is max
                                addQueryParameter("orderBy", "date")
                                addQueryParameter("order", "asc")
                                addQueryParameter("_fields", "link,date,assets")
                                addQueryParameter("ck_feature", mangaName)
                                addQueryParameter("page", (idx + 1).toString())
                            }.build().toString()
                }
            }.reversed()
        }

        val chapters = mutableListOf<SChapter>()
        var pageNum = 1

        var chapterData = getChapterList(mangaName, pageNum)
        var chapterNum = 0.0F

        while (chapterData != null) {
            val list = chapterData.map {
                chapterNum += 0.01F
                SChapter.create().apply {
                    Log.i("comics", it.link)
                    chapter_number = chapterNum
                    setUrlWithoutDomain(it.link)
                    date_upload = dateFormat.parse(it.date).time
                    name = it.date.substringBefore("T")
                }
            }
            chapters.addAll(list)

            if (list.count() < 100) {
                Log.i("comics", "End of chapterParse")
                break
            }

            pageNum++
            try {
                chapterData = getChapterList(mangaName, pageNum)
            } catch (exception: Exception) {
                if (chapters.isNotEmpty()) {
                    return chapters
                }
            }
        }

        return chapters
    }

    private fun getChapterList(mangaName: String, page: Int): List<ComicsKingdomDto.Chapter> {
        val url = "https://wp.$urlBase/wp-json/wp/v2/ck_comic".toHttpUrl().newBuilder().apply {
            addQueryParameter("per_page", "" + 100) // 100 is max
            addQueryParameter("order", "desc")
            addQueryParameter("_fields", "link,date")
            addQueryParameter("ck_feature", mangaName)
            addQueryParameter("page", "" + page)
        }.build()

        val call = client.newCall(GET(url, headers)).execute()
        val body = call.body.string()
        call.close()
        return json.decodeFromString<List<ComicsKingdomDto.Chapter>>(body)
    }

    override fun pageListRequest(chapter: SChapter): Request {
        if (chapter.url.startsWith("https://wp")) return GET(chapter.url, headers)
        return super.pageListRequest(chapter)
    }

    override fun chapterListSelector() = throw UnsupportedOperationException()
    override fun chapterFromElement(element: Element) = throw UnsupportedOperationException()

    override fun pageListParse(document: Document): List<Page> {
        if (shouldCompact()) {
            return json.decodeFromString<List<ComicsKingdomDto.Chapter>>(document.body().text())
                .mapIndexed { idx, chapter ->
                    Page(idx, imageUrl = chapter.assets!!.single.url)
                }
        }

        return listOf(
            Page(
                1,
                imageUrl = document.selectFirst(".ck-single-panel-reader img")!!
                    .attr("abs:src").toHttpUrl().queryParameter("url")!!.substringBefore("&"),
            ),
        )
    }

    private class SortFilter :
        Filter.Select<String>("Sort by", arrayOf("Newest First", "Oldest First")) {
        fun getSortQuery(): String {
            return if (state == 0) "newest" else "oldest"
        }
    }

    private class Genre(name: String, val gid: String) : Filter.CheckBox(name)
    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Genres", genres) {
        val included: List<String>
            get() = state.filter { it.state }.map { it.gid }
    }

    override fun getFilterList() = FilterList(
        SortFilter(),
        GenreList(getGenreList()),
    )

    override fun imageUrlParse(document: Document): String = ""

    // [...document.querySelectorAll(".search-checks li")].map((el) => `Genre("${el.innerText}", "${el.innerText.replaceAll(" ","+")}")`).join(',\n')
    // on https://readcomic.net/advanced-search
    private fun getGenreList() = listOf(
        Genre("Action", "action"),
        Genre("Adventure", "adventure"),
        Genre("Classic", "classic"),
        Genre("Comedy", "comedy"),
        Genre("Crime", "crime"),
        Genre("Fantasy", "fantasy"),
        Genre("Gag Cartoons", "gag-cartoons"),
        Genre("Mystery", "mystery"),
        Genre("New Arrivals", "new-arrivals"),
        Genre("Non-Fiction", "non-fiction"),
        Genre("OffBeat", "offbeat"),
        Genre("Political Cartoons", "political-cartoons"),
        Genre("Romance", "romance"),
        Genre("Sci-Fi", "sci-fi"),
        Genre("Slice Of Life", "slice-of-life"),
        Genre("Superhero", "superhero"),
        Genre("Vintage", "vintage"),
    )

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val compactpref = androidx.preference.CheckBoxPreference(screen.context).apply {
            key = "compactPref"
            title = "Compact chapters"
            summary =
                "Unchecking this will make each daily/weekly upload into a chapter which can be very slow because some comics have 8000+ uploads"
            isChecked = true

            setOnPreferenceChangeListener { _, newValue ->
                preferences.edit().putBoolean("compactPref", newValue as Boolean).commit()
            }
        }

        screen.addPreference(compactpref)
    }

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    private fun shouldCompact() = preferences.getBoolean("compactPref", true)
}
