package eu.kanade.tachiyomi.extension.all.hentairox

import eu.kanade.tachiyomi.multisrc.galleryadults.AdvancedTextFilter
import eu.kanade.tachiyomi.multisrc.galleryadults.FavoriteFilter
import eu.kanade.tachiyomi.multisrc.galleryadults.GalleryAdults
import eu.kanade.tachiyomi.multisrc.galleryadults.Genre
import eu.kanade.tachiyomi.multisrc.galleryadults.GenresFilter
import eu.kanade.tachiyomi.multisrc.galleryadults.SortOrderFilter
import eu.kanade.tachiyomi.multisrc.galleryadults.SpeechlessFilter
import eu.kanade.tachiyomi.multisrc.galleryadults.imgAttr
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class HentaiRox(
    lang: String = "all",
    override val mangaLang: String = LANGUAGE_MULTI,
) : GalleryAdults(
    "HentaiRox",
    "https://hentairox.com",
    lang = lang,
) {
    override val supportsLatest = true
    override val supportSpeechless: Boolean = true

    override val basicSearchKey = "key"

    override fun Element.mangaLang() = select("a:has(.thumb_flag)").attr("href")
        .removeSuffix("/").substringAfterLast("/")
        .let {
            // Include Speechless in search results
            if (it == LANGUAGE_SPEECHLESS) mangaLang else it
        }

    override fun Element.mangaTitle(selector: String): String? = mangaFullTitle(selector.takeIf { it != ".caption" } ?: ".gallery_title").let {
        if (preferences.shortTitle) it?.shortenTitle() else it
    }

    override fun popularMangaRequest(page: Int): Request = if (mangaLang.isBlank()) {
        // Popular browsing for LANGUAGE_MULTI
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegments("top-rated")
            addPageUri(page)
        }
        GET(url.build(), headers)
    } else {
        // Popular browsing for other languages: using source's popular page
        super.popularMangaRequest(page)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        // Basic search
        val sortOrderFilter = filters.filterIsInstance<SortOrderFilter>().firstOrNull()
        val genresFilter = filters.filterIsInstance<GenresFilter>().firstOrNull()
        val selectedGenres = genresFilter?.state?.filter { it.state } ?: emptyList()
        val favoriteFilter = filters.filterIsInstance<FavoriteFilter>().firstOrNull()

        // Speechless
        val speechlessFilter = filters.filterIsInstance<SpeechlessFilter>().firstOrNull()

        // Advanced search
        val advancedSearchFilters = filters.filterIsInstance<AdvancedTextFilter>()

        return when {
            favoriteFilter?.state == true ->
                favoriteFilterSearchRequest(page, query, filters)
            supportSpeechless && speechlessFilter?.state == true ->
                speechlessFilterSearchRequest(page, query, filters)
            supportAdvancedSearch && advancedSearchFilters.any { it.state.isNotBlank() } ->
                advancedSearchRequest(page, query, filters)
            selectedGenres.size == 1 && query.isBlank() ->
                tagBrowsingSearchRequest(page, query, filters)
            useIntermediateSearch ->
                intermediateSearchRequest(page, query, filters)
            useBasicSearch && (selectedGenres.size > 1 || query.isNotBlank()) ->
                basicSearchRequest(page, query, filters)
            sortOrderFilter?.state == 2 ->
                topRatedRequest(page)
            sortOrderFilter?.state == 1 ->
                latestUpdatesRequest(page)
            else ->
                popularMangaRequest(page)
        }
    }

    private fun topRatedRequest(page: Int): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegments("top-rated")
            addPageUri(page)
        }
        return GET(url.build(), headers)
    }

    override fun getSortOrderURIs() = listOf(
        Pair("Popular", "pp"),
        Pair("Latest", "lt"),
        Pair("Top Rated", "tr"),
    )

    /**
     * Convert space( ) typed in search-box into plus(+) in URL. Then:
     * - ignore the word preceding by a special character (e.g. 'school-girl' will ignore 'girl')
     *    => replace to plus(+),
     * - use plus(+) for separate terms, as AND condition.
     * - use double quote(") to search for exact match.
     */
    override fun buildQueryString(tags: List<String>, query: String): String {
        val regexSpecialCharacters = Regex("""[^a-zA-Z0-9"]+(?=[a-zA-Z0-9"])""")
        return (tags + query + mangaLang).filterNot { it.isBlank() }.joinToString("+") {
            it.trim().replace(regexSpecialCharacters, "+")
        }
    }

    /* Details */
    override fun Element.getInfo(tag: String): String = select("li:has(.tags_text:contains($tag)) a.tag")
        .joinToString {
            val name = it.selectFirst(".item_name")?.ownText() ?: ""
            if (tag.contains(regexTag)) {
                genres[name] = it.attr("href")
                    .removeSuffix("/").substringAfterLast('/')
            }
            listOf(
                name,
                it.select(".split_tag").text()
                    .removePrefix("| ")
                    .trim(),
            )
                .filter { s -> s.isNotBlank() }
                .joinToString()
        }

    override fun Element.getCover() = selectFirst(".left_cover img")?.imgAttr()

    override val mangaDetailInfoSelector = ".gallery_first"

    /* Pages */
    override val thumbnailSelector = ".gthumb"
    override val pageUri = "view"

    /* Filters */
    override fun tagsParser(document: Document): List<Genre> = document.select(".gtags .gallery_title a")
        .mapNotNull {
            Genre(
                it.ownText(),
                it.attr("href")
                    .removeSuffix("/").substringAfterLast('/'),
            )
        }
}
