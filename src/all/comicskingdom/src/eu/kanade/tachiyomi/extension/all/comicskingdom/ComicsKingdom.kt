package eu.kanade.tachiyomi.extension.all.comicskingdom

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

class ComicsKingdom(override val lang: String) : ConfigurableSource, HttpSource() {

    override val name = "Comics Kingdom"
    override val baseUrl = "https://wp.comicskingdom.com"
    override val supportsLatest = true

    private val json: Json by injectLazy()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
    private val compactChapterCountRegex = Regex("\"totalItems\":(\\d+)")
    private val thumbnailUrlRegex = Regex("thumbnailUrl\":\"(\\S+)\",\"dateP")

    private val mangaPerPage = 20
    private val chapterPerPage = 100

    private fun mangaApiUrl(): HttpUrl.Builder =
        baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegments("wp-json/wp/v2")
            addPathSegment("ck_feature")
            addQueryParameter("per_page", mangaPerPage.toString())
            addQueryParameter("_fields", MangaFields)
            addQueryParameter("ck_language", if (lang == "es") "spanish" else "english")
        }

    private fun chapterApiUrl(): HttpUrl.Builder = baseUrl.toHttpUrl().newBuilder().apply {
        addPathSegments("wp-json/wp/v2")
        addPathSegment("ck_comic")
        addQueryParameter("per_page", chapterPerPage.toString())
        addQueryParameter("_fields", ChapterFields)
    }

    private fun getReq(orderBy: String, page: Int): Request = GET(
        mangaApiUrl().apply {
            addQueryParameter("orderBy", orderBy)
            addQueryParameter("page", page.toString())
        }.build(),
        headers,
    )

    override fun popularMangaRequest(page: Int): Request = getReq("relevance", page)
    override fun latestUpdatesRequest(page: Int): Request = getReq("modified", page)
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
        GET(
            mangaApiUrl().apply {
                addQueryParameter("search", query)
                addQueryParameter("page", page.toString())

                if (!filters.isEmpty()) {
                    for (filter in filters) {
                        when (filter) {
                            is OrderFilter -> {
                                addQueryParameter("orderby", filter.getValue())
                            }

                            is GenreList -> {
                                if (filter.included.isNotEmpty()) {
                                    addQueryParameter(
                                        "ck_genre",
                                        filter.included.joinToString(","),
                                    )
                                }
                                if (filter.excluded.isNotEmpty()) {
                                    addQueryParameter(
                                        "ck_genre_exclude",
                                        filter.excluded.joinToString(","),
                                    )
                                }
                            }

                            else -> {}
                        }
                    }
                }
            }.build(),
            headers,
        )

    override fun searchMangaParse(response: Response): MangasPage {
        val list = json.decodeFromString<List<Manga>>(response.body.string())
        return MangasPage(
            list.map {
                SManga.create().apply {
                    thumbnail_url = thumbnailUrlRegex.find(it.yoast_head)?.groupValues?.get(1)
                    setUrlWithoutDomain(
                        mangaApiUrl().apply {
                            addPathSegment(it.id.toString())
                            addQueryParameter("slug", it.link.toHttpUrl().pathSegments.last())
                        }
                            .build().toString(),
                    )
                    title = it.title.rendered
                }
            },
            list.count() == mangaPerPage,
        )
    }

    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)
    override fun latestUpdatesParse(response: Response): MangasPage = searchMangaParse(response)

    override fun mangaDetailsParse(response: Response): SManga = SManga.create().apply {
        val mangaData = json.decodeFromString<Manga>(response.body.string())
        title = mangaData.title.rendered
        author = mangaData.meta.ck_byline_on_app.substringAfter("By").trim()
        description = Jsoup.parse(mangaData.content.rendered).text()
        status = SManga.UNKNOWN
        thumbnail_url = thumbnailUrlRegex.find(mangaData.yoast_head)?.groupValues?.get(1)
    }

    override fun getMangaUrl(manga: SManga): String =
        "$baseUrl/${(baseUrl + manga.url).toHttpUrl().queryParameter("slug")}"

    override fun chapterListParse(response: Response): List<SChapter> {
        val mangaData = json.decodeFromString<Manga>(response.body.string())
        val mangaName = mangaData.link.toHttpUrl().pathSegments.last()

        if (shouldCompact()) {
            val res = client.newCall(GET(mangaData.link)).execute()
            val postCount = compactChapterCountRegex.findAll(res.body.string())
                .find { result -> result.groupValues[1].toDouble() > 0 }!!.groupValues[1].toDouble()
            res.close()
            val maxPage = ceil(postCount / chapterPerPage)
            return List(maxPage.roundToInt()) { idx ->
                SChapter.create().apply {
                    chapter_number = idx * 0.01F
                    name =
                        "${idx * chapterPerPage + 1}-${if (postCount - (idx + 1) * chapterPerPage < 0) postCount.toInt() else (idx + 1) * chapterPerPage}"
                    setUrlWithoutDomain(
                        chapterApiUrl().apply {
                            addQueryParameter("orderBy", "date")
                            addQueryParameter("order", "asc")
                            addQueryParameter("ck_feature", mangaName)
                            addQueryParameter("page", (idx + 1).toString())
                        }.build().toString(),
                    )
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
                    chapter_number = chapterNum
                    setUrlWithoutDomain(
                        chapterApiUrl().apply {
                            addPathSegment(it.id.toString())
                            addQueryParameter("slug", it.link.substringAfter(baseUrl))
                        }
                            .toString(),
                    )
                    date_upload = dateFormat.parse(it.date).time
                    name = it.date.substringBefore("T")
                }
            }
            chapters.addAll(list)

            if (list.count() < 100) {
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

    private fun getChapterList(mangaName: String, page: Int): List<Chapter> {
        val url = chapterApiUrl().apply {
            addQueryParameter("order", "desc")
            addQueryParameter("ck_feature", mangaName)
            addQueryParameter("page", page.toString())
        }.build()

        val call = client.newCall(GET(url, headers)).execute()
        val body = call.body.string()
        call.close()
        return json.decodeFromString<List<Chapter>>(body)
    }

    override fun getChapterUrl(chapter: SChapter): String {
        if (shouldCompact()) {
            return "$baseUrl/${(baseUrl + chapter.url).toHttpUrl().queryParameter("ck_feature")}"
        }
        return "$baseUrl/${(baseUrl + chapter.url).toHttpUrl().queryParameter("slug")}"
    }

    override fun pageListParse(response: Response): List<Page> {
        if (shouldCompact()) {
            return json.decodeFromString<List<Chapter>>(response.body.string())
                .mapIndexed { idx, chapter ->
                    Page(idx, imageUrl = chapter.assets!!.single.url)
                }
        }
        val chapter = json.decodeFromString<Chapter>(response.body.string())
        return listOf(Page(0, imageUrl = chapter.assets!!.single.url))
    }

    private class OrderFilter :
        Filter.Select<String>(
            "Order by",
            arrayOf(
                "author",
                "date",
                "id",
                "include",
                "modified",
                "parent",
                "relevance",
                "title",
                "rand",
            ),
        ) {
        fun getValue(): String = values[state]
    }

    private class Genre(name: String, val gid: String) : Filter.TriState(name)
    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Genres", genres) {
        val included: List<String>
            get() = state.filter { it.isIncluded() }.map { it.gid }

        val excluded: List<String>
            get() = state.filter { it.isExcluded() }.map { it.gid }
    }

    override fun getFilterList() = FilterList(
        OrderFilter(),
        GenreList(getGenreList()),
    )

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
        }

        screen.addPreference(compactpref)
    }

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    private fun shouldCompact() = preferences.getBoolean("compactPref", true)

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
