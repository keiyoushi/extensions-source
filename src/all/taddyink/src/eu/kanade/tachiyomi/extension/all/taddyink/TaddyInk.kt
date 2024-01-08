package eu.kanade.tachiyomi.extension.all.taddyink

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
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
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.injectLazy

open class TaddyInk(
    override val lang: String,
    private val taddyLang: String,
) : ConfigurableSource, HttpSource() {

    final override val baseUrl = "https://taddy.org"
    override val name = "Taddy INK (Webtoons)"
    override val supportsLatest = false

    override val client: OkHttpClient by lazy {
        network.cloudflareClient.newBuilder()
            .rateLimit(4)
            .build()
    }

    private val json: Json by injectLazy()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = TITLE_PREF_KEY
            title = TITLE_PREF
            summaryOn = "Full Title"
            summaryOff = "Short Title"
            setDefaultValue(true)
        }.also(screen::addPreference)
    }

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException("Not used!")

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException("Not used!")

    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/feeds/directory/list".toHttpUrl().newBuilder()
            .addQueryParameter("lang", taddyLang)
            .addQueryParameter("taddyType", "comicseries")
            .addQueryParameter("ua", "tc")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", POPULAR_MANGA_LIMIT.toString())
        return GET(url.build(), headers)
    }

    override fun popularMangaParse(response: Response) = parseManga(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val filterList = if (filters.isEmpty()) getFilterList() else filters
        val shouldFilterByGenre = filterList.findInstance<GenreFilter>()?.state != 0
        val shouldFilterByCreator = filterList.findInstance<CreatorFilter>()?.state?.isNotBlank() ?: false
        val shouldFilterForTags = filterList.findInstance<TagFilter>()?.state?.isNotBlank() ?: false

        val url = "$baseUrl/feeds/directory/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("lang", taddyLang)
            .addQueryParameter("taddyType", "comicseries")
            .addQueryParameter("ua", "tc")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", SEARCH_MANGA_LIMIT.toString())

        if (shouldFilterByGenre) {
            filterList.findInstance<GenreFilter>()?.let { f ->
                url.addQueryParameter("genre", f.toUriPart())
            }
        }

        if (shouldFilterByCreator) {
            filterList.findInstance<CreatorFilter>()?.let { name ->
                url.addQueryParameter("creator", name.state)
            }
        }

        if (shouldFilterForTags) {
            filterList.findInstance<TagFilter>()?.let { tags ->
                url.addQueryParameter("tags", tags.state)
            }
        }

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response) = parseManga(response)

    private fun parseManga(response: Response): MangasPage {
        val comicSeries = json.decodeFromString<ComicResults>(response.body.string())
        val mangas = comicSeries.comicseries.map { TaddyUtils.getManga(it) }
        val hasNextPage = comicSeries.comicseries.size == POPULAR_MANGA_LIMIT
        return MangasPage(mangas, hasNextPage)
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET(manga.url, headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val comicObj = json.decodeFromString<Comic>(response.body.string())
        return TaddyUtils.getManga(comicObj)
    }

    override fun chapterListRequest(manga: SManga): Request {
        return GET(manga.url, headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val comic = json.decodeFromString<Comic>(response.body.string())
        val sssUrl = comic.url

        val chapters = comic.issues.orEmpty().mapIndexed { i, chapter ->
            SChapter.create().apply {
                url = "$sssUrl#${chapter.identifier}"
                name = chapter.name
                date_upload = TaddyUtils.getTime(chapter.datePublished)
                chapter_number = (comic.issues.orEmpty().size - i).toFloat()
            }
        }

        return chapters.reversed()
    }

    override fun pageListRequest(chapter: SChapter): Request {
        return GET(chapter.url, headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val requestUrl = response.request.url.toString()
        val issueUuid = requestUrl.substringAfterLast("#")
        val comic = json.decodeFromString<Comic>(response.body.string())

        val issue = comic.issues.orEmpty().firstOrNull { it.identifier == issueUuid }

        return issue?.stories.orEmpty().mapIndexed { index, storyObj ->
            Page(index, "", "${storyObj.storyImage?.base_url}${storyObj.storyImage?.story}")
        }
    }

    override fun imageUrlParse(response: Response) = ""

    override fun getFilterList(): FilterList = FilterList(
        GenreFilter(),
        Filter.Separator(),
        Filter.Header("Filter by the creator or tags:"),
        CreatorFilter(),
        TagFilter(),
    )

    class CreatorFilter : AdvSearchEntryFilter("Creator")
    class TagFilter : AdvSearchEntryFilter("Tags")
    open class AdvSearchEntryFilter(name: String) : Filter.Text(name)

    private class GenreFilter : UriPartFilter(
        "Filter By Genre",
        TaddyUtils.genrePairs,
    )

    private open class UriPartFilter(displayName: String, val vals: List<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    private inline fun <reified T> Iterable<*>.findInstance() = find { it is T } as? T

    companion object {
        private const val TITLE_PREF_KEY = "display_full_title"
        private const val TITLE_PREF = "Display manga title as"

        private const val POPULAR_MANGA_LIMIT = 25
        private const val SEARCH_MANGA_LIMIT = 25
    }
}
