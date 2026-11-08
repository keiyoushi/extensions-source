package eu.kanade.tachiyomi.extension.en.toonhub

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferencesLazy
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class ToonHub :
    KeiSource(),
    ConfigurableSource {

    private val preferences by getPreferencesLazy()

    // Each list of the site holds all its comics on one page
    override suspend fun getPopularManga(page: Int) = getComics("/popular/")

    override suspend fun getLatestUpdates(page: Int) = getComics("/new/")

    // The site has no search: its comics are filtered by title
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val genre = filters.firstInstance<GenreFilter>().value
        val comics = getComics(if (genre == null) "/comics/" else "/genres/$genre/").mangas
        return MangasPage(comics.filter { it.title.contains(query.trim(), ignoreCase = true) }, false)
    }

    private suspend fun getComics(path: String): MangasPage {
        val list = client.get("$baseUrl$path", Fetch.DOCUMENT).asJsoup()
            .extractNextJs<ComicListDto> { it is JsonObject && "data" in it && "totalPages" in it }!!
        return MangasPage(list.data.map { it.toSManga() }, false)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val slug = url.pathSegments.dropWhile { it != "comics" }.getOrNull(1)?.takeIf(String::isNotEmpty) ?: return null
        return getComic(slug).toSManga()
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val comic = getComic(manga.url)
        return SMangaUpdate(comic.toSManga(), comic.toSChapters(preferences.getBoolean(PREF_HIDE_LOCKED, false)))
    }

    private suspend fun getComic(slug: String) = client.get("$baseUrl/comics/$slug/", Fetch.DOCUMENT).asJsoup()
        .extractNextJs<ComicDto> { it is JsonObject && "episodes" in it && "slug" in it }!!

    override fun getMangaUrl(manga: SManga) = "$baseUrl/comics/${manga.url}/"

    // A locked episode redirects to its comic page, which has no panels
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val episode = client.get("$baseUrl${chapter.url}", Fetch.DOCUMENT).asJsoup()
            .extractNextJs<EpisodePagesDto> { it is JsonObject && "panels" in it }
            ?: return emptyList()
        return episode.panels.sortedBy { it.index }.mapIndexed { index, panel -> Page(index, imageUrl = panel.url) }
    }

    override fun getFilterList(data: JsonElement?) = getFilters()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_HIDE_LOCKED
            title = "Hide locked episodes"
            summary = "Locked episodes (🔒) need a VIP membership or coins on the site."
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    companion object {
        private const val PREF_HIDE_LOCKED = "pref_hide_locked"
    }
}
