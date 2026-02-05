package eu.kanade.tachiyomi.extension.en.yorai

import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Locale.getDefault

class Yorai :
    HttpSource(),
    ConfigurableSource {

    override val name = "Yorai"
    override val baseUrl = "https://yorai.io"

    val apiUrl = "$baseUrl/api"
    override val lang = "en"
    override val supportsLatest = true
    private val preferences = getPreferences()
    private fun quality() = preferences.getString(PREF_SOURCE_QUALITY, "default")

    companion object {
        private const val PREF_SOURCE_QUALITY = "pref_source_quality"
    }

    // Popular
    override fun popularMangaRequest(page: Int): Request = GET("$apiUrl/manga/browse?page=$page&sort=popular", headers)

    // Latest
    override fun latestUpdatesRequest(page: Int): Request = GET("$apiUrl/manga/browse?page=$page&sort=updated_at", headers)

    // Search
    override fun getFilterList(): FilterList = getFilters()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiUrl/manga/browse".toHttpUrl().newBuilder().apply {
            val tags: MutableList<String> = mutableListOf()
            val genres: MutableList<String> = mutableListOf()
            filters.forEach { filter ->
                when (filter) {
                    is SortFilter -> {
                        addQueryParameter("sort", filter.selected)
                        addQueryParameter("order", if (filter.state!!.ascending) "asc" else "desc")
                    }

                    is GenreFilter -> {
                        val (activeFilters, _) = filter.state.partition { stIt -> stIt.state }
                        activeFilters.forEach {
                            genres.add(it.name.lowercase().replace(" ", "_"))
                        }
                    }

                    is StatusFilter -> {
                        addQueryParameter("status", filter.selected)
                    }

                    is TypeFilter -> {
                        addQueryParameter("type", filter.selected)
                    }

                    else -> {}
                }
            }
            addQueryParameter("page", page.toString())
            addQueryParameter("genre", genres.joinToString(","))
            addQueryParameter("search", query)
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val data = response.parseAs<Browse>()

        with(data) {
            val hasNextPage = pagination.page <= pagination.totalPages
            return MangasPage(
                series.map {
                    SManga.create().apply {
                        url = "/manga/${it.id}"
                        title = it.title
                        thumbnail_url = baseUrl + it.coverImage
                    }
                },
                hasNextPage,
            )
        }
    }
    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)
    override fun latestUpdatesParse(response: Response): MangasPage = searchMangaParse(response)

    override fun mangaDetailsRequest(manga: SManga): Request = GET(apiUrl + manga.url, headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val data = response.parseAs<DetailSeries>()
        val genres = data.genres.joinToString(", ") { g ->
            g.replace("_", " ")
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(getDefault()) else it.toString() }
        }
        val tags = data.tags.joinToString(", ") { "⟡$it" }
        return SManga.create().apply {
            author = data.author
            artist = data.artist
            genre = "$genres, $tags, ${data.type}"
            description = buildString {
                data.description?.let { append(it, "\n") }
                data.year?.let { append("Released: $it") }
            }
            status = when (data.status) {
                "releasing" -> SManga.ONGOING
                "completed" -> SManga.COMPLETED
                "cancelled" -> SManga.CANCELLED
                "hiatus" -> SManga.ON_HIATUS
                else -> SManga.UNKNOWN
            }
        }
    }
    // Chapters

    override fun chapterListRequest(manga: SManga) = GET(apiUrl + manga.url + "/chapters", headers)

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH)
    override fun chapterListParse(response: Response): List<SChapter> {
        val chapters = response.parseAs<Chapters>()

        return chapters.chapters.asReversed().map {
            val q = getQualityName(it)
            SChapter.create().apply {
                url = "/manga/${chapters.seriesId}/chapters/${it.number}?source=" + if (q.id == it.defaultSource) "" else q.id
                name = it.title
                chapter_number = it.number
                date_upload = dateFormat.tryParse(it.releaseDate)
                scanlator = if (q.id == it.defaultSource) null else q.name
            }
        }
    }

    // Pages
    override fun pageListRequest(chapter: SChapter): Request = GET(apiUrl + chapter.url, headers)

    override fun pageListParse(response: Response): List<Page> {
        val data = response.parseAs<DetailedChapter>().pages
        return data.map {
            Page(it.number, imageUrl = baseUrl + it.url)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // Settings
    private fun getQualityName(entry: Chapters.Chapter): Chapters.Chapter.Source {
        val default = entry.sources.firstOrNull { it.id == entry.defaultSource }
        return entry.sources.firstOrNull { it.quality == quality() } ?: default!!
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = PREF_SOURCE_QUALITY
            title = "Source Quality"
            summary = "Select Source Quality"
            entries = arrayOf("Default", "Medium")
            entryValues = arrayOf("default", "medium")
            setDefaultValue("default")
        }.also(screen::addPreference)
    }
}
