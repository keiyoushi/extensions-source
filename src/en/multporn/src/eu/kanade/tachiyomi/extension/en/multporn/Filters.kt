package eu.kanade.tachiyomi.extension.en.multporn

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import java.util.Locale

const val LATEST_DEFAULT_SORT_BY_FILTER_STATE = 3
const val POPULAR_DEFAULT_SORT_BY_FILTER_STATE = 0
const val SEARCH_DEFAULT_SORT_BY_FILTER_STATE = 5

const val LATEST_REQUEST_TYPE = "Latest"
const val POPULAR_REQUEST_TYPE = "Popular"
const val SEARCH_REQUEST_TYPE = "Search"

private val nonAlphanumericRegex = Regex("[^A-Za-z0-9]")
private val whitespaceRegex = Regex("\\s+")

open class URIFilter(open val name: String, open val uri: String)

class RequestTypeURIFilter(
    val requestType: String,
    override var name: String,
    override val uri: String,
) : URIFilter(name, uri)

open class URISelectFilter(name: String, open val filters: List<URIFilter>, state: Int = 0) : Filter.Select<String>(name, filters.map { it.name }.toTypedArray(), state) {
    open val selected: URIFilter
        get() = filters[state]
}

open class TypeSelectFilter(name: String, filters: List<URIFilter>) : URISelectFilter(name, filters)

class PopularTypeSelectFilter(filters: List<URIFilter>) : TypeSelectFilter("Popular Type", filters)

class LatestTypeSelectFilter(filters: List<URIFilter>) : TypeSelectFilter("Latest Type", filters)

class SearchTypeSelectFilter(filters: List<URIFilter>) : TypeSelectFilter("Search Type", filters)

open class TextSearchFilter(name: String, val uri: String) : Filter.Text(name) {
    val stateURIs: List<String>
        get() = state.split(",").filter { it.isNotEmpty() }.map {
            nonAlphanumericRegex.replace(it, " ").trim()
                .replace(whitespaceRegex, "_")
                .lowercase(Locale.ROOT)
        }.distinct()
}

class SortBySelectFilter(override val filters: List<RequestTypeURIFilter>, state: Int) :
    URISelectFilter(
        "Sort By",
        filters.map { filter ->
            filter.let {
                it.name = "[${it.requestType}] ${it.name}"
                it
            }
        },
        state,
    ) {
    val requestType: String
        get() = filters[state].requestType
}

class SortOrderSelectFilter(filters: List<URIFilter>) : URISelectFilter("Order By", filters)

fun getMultpornFilterList(sortByFilterState: Int) = FilterList(
    Filter.Header("Text search only works with Relevance and Author"),
    SortBySelectFilter(getSortByFilters(), sortByFilterState),
    Filter.Header("Order By only works with Popular and Latest"),
    SortOrderSelectFilter(getSortOrderFilters()),
    Filter.Header("Type filters apply based on selected Sort By option"),
    PopularTypeSelectFilter(getPopularTypeFilters()),
    LatestTypeSelectFilter(getLatestTypeFilters()),
    SearchTypeSelectFilter(getSearchTypeFilters()),
    Filter.Separator(),
    Filter.Header("Filters below ignore text search and all options above"),
    Filter.Header("Query must match title's non-special characters"),
    Filter.Header("Separate queries with comma (,)"),
    TextSearchFilter("Comic Tags", "category"),
    TextSearchFilter("Comic Characters", "characters"),
    TextSearchFilter("Comic Authors", "authors_comics"),
    TextSearchFilter("Comic Sections", "comics"),
    TextSearchFilter("Manga Categories", "category_hentai"),
    TextSearchFilter("Manga Characters", "characters_hentai"),
    TextSearchFilter("Manga Authors", "authors_hentai_comics"),
    TextSearchFilter("Manga Sections", "hentai_manga"),
    TextSearchFilter("Picture Authors", "authors_albums"),
    TextSearchFilter("Picture Sections", "pictures"),
    TextSearchFilter("Hentai Sections", "hentai"),
    TextSearchFilter("Rule 63 Sections", "rule_63"),
    TextSearchFilter("Gay Tags", "category_gay"),
)

private fun getPopularTypeFilters() = listOf(
    URIFilter("Comics", "1"),
    URIFilter("Hentai Manga", "2"),
    URIFilter("Cartoon Pictures", "3"),
    URIFilter("Hentai Pictures", "4"),
    URIFilter("Rule 63", "10"),
    URIFilter("Author Albums", "11"),
)

private fun getLatestTypeFilters() = listOf(
    URIFilter("Comics", "1"),
    URIFilter("Hentai Manga", "2"),
    URIFilter("Cartoon Pictures", "3"),
    URIFilter("Hentai Pictures", "4"),
    URIFilter("Author Albums", "10"),
)

private fun getSearchTypeFilters() = listOf(
    URIFilter("Comics", "1"),
    URIFilter("Hentai Manga", "2"),
    URIFilter("Gay Comics", "3"),
    URIFilter("Cartoon Pictures", "4"),
    URIFilter("Hentai Pictures", "5"),
    URIFilter("Rule 63", "11"),
    URIFilter("Humor", "13"),
)

private fun getSortByFilters() = listOf(
    RequestTypeURIFilter(POPULAR_REQUEST_TYPE, "Total Views", "totalcount_1"),
    RequestTypeURIFilter(POPULAR_REQUEST_TYPE, "Views Today", "daycount"),
    RequestTypeURIFilter(POPULAR_REQUEST_TYPE, "Last Viewed", "timestamp"),
    RequestTypeURIFilter(LATEST_REQUEST_TYPE, "Date Posted", "created"),
    RequestTypeURIFilter(LATEST_REQUEST_TYPE, "Date Updated", "changed"),
    RequestTypeURIFilter(SEARCH_REQUEST_TYPE, "Relevance", "search_api_relevance"),
    RequestTypeURIFilter(SEARCH_REQUEST_TYPE, "Author", "author"),
)

private fun getSortOrderFilters() = listOf(
    URIFilter("Descending", "DESC"),
    URIFilter("Ascending", "ASC"),
)
