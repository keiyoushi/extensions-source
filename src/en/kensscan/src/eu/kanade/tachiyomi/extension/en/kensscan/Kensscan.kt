package eu.kanade.tachiyomi.extension.en.kensscan

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.multisrc.madara.GenreRoute
import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
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
import keiyoushi.utils.asJsoup
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.textOrNull
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@Source
abstract class Kensscan :
    MadaraNoAjax(),
    ConfigurableSource {

    private val preferences = getPreferences()

    override val supportsPostId = false

    override fun archiveSelector() = "div.manga-item, .c-tabs-item__content"

    override val archiveUrlSelector = "h3.manga-title a, .post-title a"

    override fun nextPageSelector() = "a.next.page-numbers"

    override fun archiveManga(element: Element, id: String) = super.archiveManga(element, id)?.apply {
        genre = element.select(".manga-genre-list a, .mg_genres a").eachText().joinToString().ifBlank { null }
        element.selectFirst(".mg_status .summary-content")?.text()?.let { status = it.toStatus() }
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = searchUrlBuilder(page, query.trim()).apply {
            filters.forEach { filter ->
                when (filter) {
                    is TextFilter -> if (filter.state.isNotBlank()) addQueryParameter(filter.param, filter.state.trim())
                    is StatusList -> filter.state.filter { it.state }.forEach { addQueryParameter("status[]", it.value) }
                    is GenreConditionFilter -> filter.value.takeIf(String::isNotEmpty)?.let { addQueryParameter("op", it) }
                    is GenreList -> filter.state.filter { it.state }.forEach { addQueryParameter("genre[]", it.slug) }
                    is SortFilter -> filter.value.takeIf(String::isNotEmpty)?.let { addQueryParameter(orderQueryParameter, it) }
                    else -> Unit
                }
            }
        }.build()
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        return MangasPage(parseArchive(document), document.selectFirst(nextPageSelector()) != null)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val segments = url.encodedPathSegments.filter(String::isNotEmpty)
        if (segments.size < 2 || segments[0] != mangaSubString) return null
        val seriesUrl = url.newBuilder().encodedPath("/$mangaSubString/${segments[1]}/").query(null).build()
        val manga = super.getMangaByUrl(seriesUrl) ?: return null
        searchCard(manga.url, manga.title)?.let { manga.applySearchCard(it) }
        return manga
    }

    override val mangaDetailsSelectorTitle = "h1.title"
    override val mangaDetailsSelectorDescription = "div.manga-meta-item.description"

    override fun parseDetails(document: Document, id: String, preserveUrl: String?): SManga = super.parseDetails(document, id, preserveUrl).apply {
        thumbnail_url = document.selectFirst("meta[property=og:image]")?.absUrl("content")
    }

    // The series page has no author, artist, genres, status or alternative names; the site's search cards do
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        if (!fetchDetails) return SMangaUpdate(manga, super.fetchMangaUpdate(manga, chapters, false, fetchChapters).chapters)
        val update = super.fetchMangaUpdate(manga, chapters, true, fetchChapters)
        update.manga.genre = manga.genre
        update.manga.status = manga.status
        searchCard(update.manga.url, update.manga.title)?.let { update.manga.applySearchCard(it) }
        return update
    }

    override fun chapterFromElement(element: Element, mangaPath: String): SChapter? {
        val locked = element.hasClass("kvp-row--locked")
        if (locked && !preferences.getBoolean(PREF_SHOW_LOCKED, false)) return null
        return super.chapterFromElement(element, mangaPath)?.apply {
            if (locked) name = "🔒 $name"
        }
    }

    override fun parsePages(document: Document): List<Page> {
        if (document.selectFirst("section.kvp-lock") != null) {
            error("VIP early access chapter. Log in via WebView with a VIP account to read it.")
        }
        return super.parsePages(document)
    }

    override suspend fun fetchFilterData(): JsonElement = client.get(searchUrlBuilder(1, "").build(), Fetch.DOCUMENT).asJsoup()
        .select("div.checkbox-group input[name='genre[]']")
        .map { input ->
            val slug = input.attr("value")
            GenreRoute(input.nextElementSibling()!!.text(), slug, "/$genreDirectory/$slug/")
        }.toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<List<GenreRoute>>().orEmpty()
        return FilterList(
            buildList {
                add(TextFilter(intl["artist_filter_title"], "artist"))
                add(TextFilter(intl["year_filter_title"], "release"))
                add(StatusList(intl["status_filter_title"], statusFilterOptions))
                add(SortFilter(intl["order_by_filter_title"]))
                if (genres.isNotEmpty()) {
                    add(GenreConditionFilter(intl["genre_condition_filter_title"], genreConditionFilterOptions))
                    add(GenreList(intl["genre_filter_title"], genres))
                }
            },
        )
    }

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        .select(".manga-related-section div.manga-item")
        .mapNotNull { archiveManga(it, "") }

    // The site's search misses typographic punctuation and treats queries over 9 words as one phrase
    private suspend fun searchCard(path: String, title: String): Element? {
        val query = title.split(NON_WORD_REGEX).filter(String::isNotEmpty).take(9).joinToString(" ")
        return client.get(searchUrlBuilder(1, query).build(), Fetch.DOCUMENT).asJsoup()
            .select(searchCardSelector())
            .firstOrNull { card -> card.selectFirst(".post-title a")?.absUrl("href")?.toHttpUrlOrNull()?.encodedPath == path }
    }

    private fun SManga.applySearchCard(card: Element) {
        author = card.select(".mg_author .summary-content a").eachText().joinToString().ifBlank { null }
        artist = card.select(".mg_artists .summary-content a").eachText().joinToString().ifBlank { null }
        card.select(".mg_genres a").eachText().joinToString().ifBlank { null }?.let { genre = it }
        card.selectFirst(".mg_status .summary-content")?.text()?.let { status = it.toStatus() }
        card.selectFirst(".mg_alternative .summary-content")?.textOrNull()?.let { alternative ->
            description = listOfNotNull(description, "${intl["alt_names_heading"]} $alternative").joinToString("\n\n")
        }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_SHOW_LOCKED
            title = "Show VIP early access chapters"
            summary = "Listed with 🔒. Reading them needs a VIP account logged in via WebView."
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    companion object {
        private const val PREF_SHOW_LOCKED = "pref_show_locked_chapters"
        private val NON_WORD_REGEX = Regex("""[^\p{L}\p{N}]+""")
    }
}
