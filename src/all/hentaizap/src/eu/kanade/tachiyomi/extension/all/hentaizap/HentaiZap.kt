package eu.kanade.tachiyomi.extension.all.hentaizap

import eu.kanade.tachiyomi.multisrc.galleryadults.CategoryFilters
import eu.kanade.tachiyomi.multisrc.galleryadults.FavoriteFilter
import eu.kanade.tachiyomi.multisrc.galleryadults.GalleryAdults
import eu.kanade.tachiyomi.multisrc.galleryadults.Genre
import eu.kanade.tachiyomi.multisrc.galleryadults.GenresFilter
import eu.kanade.tachiyomi.multisrc.galleryadults.RandomEntryFilter
import eu.kanade.tachiyomi.multisrc.galleryadults.SortOrderFilter
import eu.kanade.tachiyomi.multisrc.galleryadults.SpeechlessFilter
import eu.kanade.tachiyomi.multisrc.galleryadults.imgAttr
import eu.kanade.tachiyomi.multisrc.galleryadults.toBinary
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import kotlin.collections.set

class HentaiZap(
    lang: String = "all",
    override val mangaLang: String = LANGUAGE_MULTI,
) : GalleryAdults(
    "HentaiZap",
    "https://hentaizap.com",
    lang = lang,
) {
    override val supportsLatest = true
    override val supportSpeechless: Boolean = true

    override fun Element.mangaLang() = select("a:has(.th_lg)").attr("href")
        .removeSuffix("/").substringAfterLast("/")
        .let {
            // Include Speechless in search results
            if (it == LANGUAGE_SPEECHLESS) mangaLang else it
        }

    /* Popular */
    override fun popularMangaRequest(page: Int): Request = if (mangaLang.isBlank()) {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegments("popular")
            addPageUri(page)
        }
        GET(url.build(), headers)
    } else {
        super.popularMangaRequest(page)
    }

    /* Search */
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        // Basic search
        val genresFilter = filters.filterIsInstance<GenresFilter>().firstOrNull()
        val selectedGenres = genresFilter?.state?.filter { it.state } ?: emptyList()
        val favoriteFilter = filters.filterIsInstance<FavoriteFilter>().firstOrNull()

        // Speechless
        val speechlessFilter = filters.filterIsInstance<SpeechlessFilter>().firstOrNull()

        return when {
            favoriteFilter?.state == true ->
                favoriteFilterSearchRequest(page, query, filters)
            supportSpeechless && speechlessFilter?.state == true ->
                speechlessFilterSearchRequest(page, query, filters)
            selectedGenres.size == 1 && query.isBlank() ->
                tagBrowsingSearchRequest(page, query, filters)
            useBasicSearch && (selectedGenres.size > 1 || query.isNotBlank()) ->
                basicSearchRequest(page, query, filters)
            else ->
                browsingWithFilters(page, filters)
        }
    }

    override val basicSearchKey = "key"

    /**
     * This supports filter query search with languages, categories (manga, doujinshi...)
     * with additional sort orders.
     */
    private fun browsingWithFilters(page: Int, filters: FilterList): Request {
        // Basic search
        val sortOrderFilter = filters.filterIsInstance<SortOrderFilter>().firstOrNull()

        // Intermediate search
        val categoryFilters = filters.filterIsInstance<CategoryFilters>().firstOrNull()

        // Only for query string or multiple tags
        val url = "$baseUrl/search/".toHttpUrl().newBuilder().apply {
            addQueryParameter("filter", "yes")
            getSortOrderURIs().forEachIndexed { index, pair ->
                addQueryParameter(pair.second, toBinary(sortOrderFilter?.state == index))
            }
            categoryFilters?.state?.forEach {
                addQueryParameter(it.uri, toBinary(it.state))
            }
            getLanguageURIs().forEach { pair ->
                addQueryParameter(
                    pair.second,
                    toBinary(mangaLang == pair.first || mangaLang == LANGUAGE_MULTI),
                )
            }
            addPageUri(page)
        }
        return GET(url.build(), headers)
    }

    override val favoritePath = "inc/user.php?act=favs"

    /* Details */
    override val mangaDetailInfoSelector = ".gp_top"

    override fun Element.getCover() = selectFirst(".gp_cover img")?.imgAttr()

    override fun Element.getInfo(tag: String): String = select(".info_txt:contains($tag:) ~ li a.gp_btn_tag")
        .joinToString {
            val name = it.ownText()
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

    override val thumbnailSelector = ".gp_th"

    override fun tagsParser(document: Document): List<Genre> = document.select("a.btn:has(.list_tag)")
        .mapNotNull {
            Genre(
                it.select(".list_tag").text(),
                it.attr("href")
                    .removeSuffix("/").substringAfterLast('/'),
            )
        }

    override fun getFilterList(): FilterList {
        requestTags()
        val filters = emptyList<Filter<*>>().toMutableList()

        filters.add(Filter.Header("HINT: Separate search term with comma (,)"))

        if (genres.isEmpty()) {
            filters.add(Filter.Header("Press 'reset' to attempt to load tags"))
        } else {
            filters.add(GenresFilter(genres))
        }

        filters.add(SortOrderFilter(getSortOrderURIs()))
        filters.add(Filter.Header("String query search doesn't support Sort"))

        filters.add(CategoryFilters(getCategoryURIs()))

        filters.add(Filter.Separator())
        filters.add(SpeechlessFilter())
        filters.add(FavoriteFilter())
        filters.add(RandomEntryFilter())

        return FilterList(filters)
    }

    override fun getSortOrderURIs() = listOf(
        Pair("Popular", "pp"),
        Pair("Latest", "lt"),
        Pair("Downloads", "dl"),
        Pair("Top Rated", "tr"),
    )

    override fun relatedMangaSelector() = ".rl_th ${popularMangaSelector()}"
}
