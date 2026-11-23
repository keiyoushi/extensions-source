package eu.kanade.tachiyomi.extension.en.ninkocomics

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class NinkoComics :
    KeiSource(),
    ConfigurableSource {

    private val preferences = getPreferences()

    override val imageFetch = Fetch.NONE

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = SHOW_LOCKED_PREF
            title = "Show locked chapters"
            summary = "Early access chapters that cost coins until their free day"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    override suspend fun getPopularManga(page: Int): MangasPage = getComics("popular", "", "")

    override suspend fun getLatestUpdates(page: Int): MangasPage = getComics("latest", "", "")

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isBlank()) {
            return getComics(
                filters.firstInstance<SortFilter>().value,
                filters.firstInstance<GenreFilter>().value,
                filters.firstInstance<StatusFilter>().value,
            )
        }
        val url = siteUrl("search").addQueryParameter("q", query.trim()).build()
        return MangasPage(client.get(url, Fetch.DOCUMENT).asJsoup().comics(), false)
    }

    // The whole catalogue is on one page
    private suspend fun getComics(sort: String, genre: String, status: String): MangasPage {
        val url = siteUrl("comics").apply {
            addQueryParameter("sort", sort)
            if (genre.isNotEmpty()) addQueryParameter("genre", genre)
            if (status.isNotEmpty()) addQueryParameter("status", status)
        }.build()
        return MangasPage(client.get(url, Fetch.DOCUMENT).asJsoup().comics(), false)
    }

    private fun Document.comics(): List<SManga> = select("a.rk-poster[href^=/comic/]").map { poster ->
        SManga.create().apply {
            setUrlWithoutDomain(poster.absUrl("href"))
            title = poster.selectFirst("span.line-clamp-2")!!.text()
            thumbnail_url = poster.selectFirst("img")?.absUrl("src")
        }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host) return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf(String::isNotEmpty) ?: return null
        val path = when (url.pathSegments[0]) {
            "comic" -> "/comic/$slug/"
            // A chapter page links back to its comic
            "chapter" -> client.get(siteUrl("chapter", slug).build(), Fetch.DOCUMENT).asJsoup()
                .selectFirst("a[href^=/comic/]")!!.absUrl("href").toHttpUrl().encodedPath
            else -> return null
        }
        val manga = SManga.create().apply { this.url = path }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    // Details and chapters come from the same page
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        val series = document.selectFirst("script[type=application/ld+json]:containsData(ComicSeries)")!!.data().parseAs<SeriesDto>()
        // Badges above the title: type, status and release day
        val badges = document.selectFirst("h1")!!.previousElementSibling()?.children()?.map { it.text() }.orEmpty()
        val status = when {
            "Ongoing" in badges -> SManga.ONGOING
            "Completed" in badges -> SManga.COMPLETED
            "Hiatus" in badges -> SManga.ON_HIATUS
            "Dropped" in badges -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        val details = series.toSManga(
            manga.url,
            document.selectFirst("[data-clamp]"),
            badges.firstOrNull { it.startsWith("New chapter") },
            status,
        )

        val showLocked = preferences.getBoolean(SHOW_LOCKED_PREF, false)
        val chapterList = document.select("li[data-chapter-id] > a").mapNotNull { link ->
            // Early access chapters cost coins until their free day
            val locked = link.hasAttr("data-unlock")
            if (locked && !showLocked) return@mapNotNull null
            SChapter.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                val title = link.selectFirst("span.min-w-0 > span")!!.text()
                name = if (locked) "🔒 $title" else title
                date_upload = DATE_FORMAT.tryParseDate(link.selectFirst("[data-percent-label]")?.attr("title"))
            }
        }
        return SMangaUpdate(details, chapterList)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = getChapterUrl(chapter)
        val document = client.get(chapterUrl, Fetch.DOCUMENT).asJsoup()
        val pagesUrl = document.selectFirst("[data-pages-url]")?.absUrl("data-pages-url")
            ?: return emptyList()
        return client.get(pagesUrl, Fetch.XHR.from(chapterUrl)).parseAs<PagesDto>().pages
            .mapIndexed { index, page ->
                Page(index, imageUrl = baseUrl.toHttpUrl().resolve(page.url)!!.toString())
            }
    }

    override fun getFilterList(data: JsonElement?): FilterList = getFilters()

    // Pages end with a slash, as the site links them
    private fun siteUrl(vararg segments: String): HttpUrl.Builder = baseUrl.toHttpUrl().newBuilder().apply {
        segments.forEach { addPathSegment(it) }
        addPathSegment("")
    }

    companion object {
        private const val SHOW_LOCKED_PREF = "show_locked"
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)
    }
}
