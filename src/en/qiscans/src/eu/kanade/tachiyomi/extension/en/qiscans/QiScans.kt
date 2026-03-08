package eu.kanade.tachiyomi.extension.en.qiscans

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.multisrc.iken.GenreFilter
import eu.kanade.tachiyomi.multisrc.iken.Iken
import eu.kanade.tachiyomi.multisrc.iken.SelectFilter
import eu.kanade.tachiyomi.multisrc.iken.UrlPartFilter
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser
import rx.Observable
import rx.schedulers.Schedulers
import java.util.concurrent.TimeUnit

class QiScans :
    Iken(
        "Qi Scans",
        "en",
        "https://qimanhwa.com",
        "https://api.qimanhwa.com",
    ) {

    override val client = super.client.newBuilder()
        .rateLimit(3, 1, TimeUnit.SECONDS)
        .build()

    override val usePopularMangaApi = true

    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)

    override fun searchMangaParse(response: Response): MangasPage = super.searchMangaParse(response).apply {
        mangas.forEach(::normalizeMangaTextFields)
    }

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = super.fetchMangaDetails(manga).map {
        it.apply(::normalizeMangaTextFields)
    }

    @Serializable
    class PageParseDto(
        val url: String,
        val order: Int,
    )

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        if (document.isLockedChapterPage()) {
            throw Exception("Paid chapter unavailable.")
        }

        val imagesJson = runCatching { document.getNextJson("images") }
            .getOrElse {
                if (document.isLockedChapterPage()) {
                    throw Exception("Paid chapter unavailable.")
                }
                throw it
            }

        return imagesJson.parseAs<List<PageParseDto>>().sortedBy { it.order }.mapIndexed { idx, p ->
            Page(idx, imageUrl = p.url.replace(" ", "%20"))
        }
    }

    private fun Document.isLockedChapterPage(): Boolean {
        if (selectFirst("svg.lucide-lock") != null) return true

        val text = body().text()
        return text.contains("unlock chapter", ignoreCase = true) ||
            text.contains("chapter locked", ignoreCase = true) ||
            text.contains("paid chapter", ignoreCase = true) ||
            text.contains("purchase", ignoreCase = true) ||
            text.contains("coins", ignoreCase = true)
    }

    private fun normalizeMangaTextFields(manga: SManga) {
        manga.title = decodeHtmlEntities(manga.title)
        manga.author = manga.author?.let(::decodeHtmlEntities)
        manga.artist = manga.artist?.let(::decodeHtmlEntities)
        manga.description = manga.description?.let(::decodeHtmlEntities)
        manga.genre = manga.genre?.let(::decodeHtmlEntities)
    }

    private var genresList: List<Pair<String, String>> = emptyList()
    private var fetchGenresAttempts = 0

    private fun fetchGenres() {
        try {
            val response = client.newCall(GET("$apiUrl/api/genres", headers)).execute()
            genresList = response.parseAs<List<GenreDto>>()
                .map { Pair(it.name, it.id.toString()) }
        } catch (e: Throwable) {
        } finally {
            fetchGenresAttempts++
        }
    }

    override fun getFilterList(): FilterList {
        if (genresList.isEmpty() && fetchGenresAttempts < 3) {
            Observable.fromCallable { fetchGenres() }
                .subscribeOn(Schedulers.io())
                .subscribe()
        }

        val filters = mutableListOf<Filter<*>>(
            SortFilter(),
            StatusFilter(),
        )

        if (genresList.isNotEmpty()) {
            filters.add(GenreFilter(genresList))
        } else {
            filters.add(Filter.Header("Press 'Reset' to attempt to load genres"))
        }
        return FilterList(filters)
    }

    private class SortFilter :
        Filter.Select<String>(
            "Sort",
            OPTIONS.map { it.first }.toTypedArray(),
        ),
        UrlPartFilter {
        override fun addUrlParameter(url: HttpUrl.Builder) {
            val (_, orderBy, orderDirection) = OPTIONS[state]
            url.addQueryParameter("orderBy", orderBy)
            if (orderDirection != null) {
                url.addQueryParameter("orderDirection", orderDirection)
            }
        }

        companion object {
            private val OPTIONS = listOf(
                Triple("Most Views", "totalViews", null),
                Triple("Latest", "lastChapterAddedAt", null),
                Triple("Newly Added", "createdAt", null),
                Triple("Title (A-Z)", "postTitle", "asc"),
                Triple("Title (Z-A)", "postTitle", "desc"),
            )
        }
    }

    private class StatusFilter :
        SelectFilter(
            "Status",
            "seriesStatus",
            listOf(
                Pair("All", ""),
                Pair("Ongoing", "ONGOING"),
                Pair("Hiatus", "HIATUS"),
                Pair("Dropped", "DROPPED"),
                Pair("Completed", "COMPLETED"),
            ),
        )

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = SHOW_LOCKED_CHAPTER_PREF_KEY
            title = "Display paid chapters"
            summaryOn = "Paid chapters will appear."
            summaryOff = "Only free chapters will be displayed."
            setDefaultValue(true)
        }.also(screen::addPreference)
    }

    companion object {
        private fun decodeHtmlEntities(value: String): String = Parser.unescapeEntities(value, false)
    }
}
