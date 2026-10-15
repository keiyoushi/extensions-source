package eu.kanade.tachiyomi.extension.all.taddyink

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import kotlin.time.Instant

@Source
abstract class TaddyInk :
    KeiSource(),
    ConfigurableSource {

    private val taddyLang = ""

    override val supportsLatest = false

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(4)

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = TITLE_PREF_KEY
            title = TITLE_PREF
            summaryOn = "Full Title"
            summaryOff = "Short Title"
            setDefaultValue(true)
        }.also(screen::addPreference)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$baseUrl/feeds/directory/list".toHttpUrl().newBuilder()
            .addQueryParameter("lang", taddyLang)
            .addQueryParameter("taddyType", "comicseries")
            .addQueryParameter("ua", "tc")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", POPULAR_MANGA_LIMIT.toString())
            .build()

        return parseManga(client.get(url).parseAs())
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        // pathSegments[1] is the slug used to identify the comic
        url.pathSegments.getOrNull(1) ?: return null

        val comic = client.get(url).parseAs<Comic>()
        return TaddyUtils.getManga(comic).apply { this.url = url.toString() }
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val genreFilter = filters.firstInstanceOrNull<GenreFilter>()
        val creatorFilter = filters.firstInstanceOrNull<CreatorFilter>()
        val tagFilter = filters.firstInstanceOrNull<TagFilter>()

        val url = "$baseUrl/feeds/directory/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("lang", taddyLang)
            .addQueryParameter("taddyType", "comicseries")
            .addQueryParameter("ua", "tc")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", SEARCH_MANGA_LIMIT.toString())

        if (genreFilter != null && genreFilter.state != 0) {
            url.addQueryParameter("genre", genreFilter.toUriPart())
        }

        if (creatorFilter != null && creatorFilter.state.isNotBlank()) {
            url.addQueryParameter("creator", creatorFilter.state)
        }

        if (tagFilter != null && tagFilter.state.isNotBlank()) {
            url.addQueryParameter("tags", tagFilter.state)
        }

        return parseManga(client.get(url.build()).parseAs())
    }

    private fun parseManga(comicSeries: ComicResults): MangasPage {
        val mangas = comicSeries.comicseries.map { TaddyUtils.getManga(it) }
        val hasNextPage = comicSeries.comicseries.size == POPULAR_MANGA_LIMIT
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val comic = client.get(manga.url).parseAs<Comic>()
        val sssUrl = comic.url
        val issues = comic.issues.orEmpty()

        val chapterList = issues.mapIndexed { i, chapter ->
            SChapter.create().apply {
                url = "$sssUrl#${chapter.identifier}"
                name = chapter.name
                date_upload = Instant.tryParse(chapter.datePublished)
                chapter_number = (issues.size - i).toFloat()
            }
        }

        return SMangaUpdate(TaddyUtils.getManga(comic), chapterList.reversed())
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val issueUuid = chapter.url.substringAfterLast("#")
        val comic = client.get(chapter.url).parseAs<Comic>()

        val issue = comic.issues.orEmpty().firstOrNull { it.identifier == issueUuid }

        return issue?.stories.orEmpty().mapIndexed { index, storyObj ->
            Page(index, "", "${storyObj.storyImage?.baseUrl}${storyObj.storyImage?.story}")
        }
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        GenreFilter(),
        Filter.Separator(),
        Filter.Header("Filter by the creator or tags:"),
        CreatorFilter(),
        TagFilter(),
    )

    class CreatorFilter : AdvSearchEntryFilter("Creator")
    class TagFilter : AdvSearchEntryFilter("Tags")
    open class AdvSearchEntryFilter(name: String) : Filter.Text(name)

    private class GenreFilter :
        UriPartFilter(
            "Filter By Genre",
            TaddyUtils.genrePairs,
        )

    private open class UriPartFilter(displayName: String, val vals: List<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    companion object {
        private const val TITLE_PREF_KEY = "display_full_title"
        private const val TITLE_PREF = "Display manga title as"

        private const val POPULAR_MANGA_LIMIT = 25
        private const val SEARCH_MANGA_LIMIT = 25
    }
}
