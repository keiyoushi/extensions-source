package eu.kanade.tachiyomi.extension.en.ebookrenta

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl.Builder

val FACETS = listOf("genm", "gend", "keyword", "deals", "keyword_sales")

open class SelectFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    val value: String
        get() = vals[state].second
}

fun Builder.addFilter(param: String, filter: SelectFilter?) = filter?.value?.takeIf(String::isNotEmpty)?.let { addEncodedQueryParameter(param, it) }

class SortFilter :
    SelectFilter(
        "Sort by",
        arrayOf(
            "Latest" to "new",
            "Review" to "review",
            "A-Z" to "abc",
            "Weekly rank" to "rank_w",
            "Monthly rank" to "rank_m",
        ),
    )

class FormatFilter :
    SelectFilter(
        "Format",
        arrayOf(
            "All" to "",
            "Comic" to "c",
            "VertiComix" to "t",
        ),
    )

class GenreFilter(facets: List<Facet>) : SelectFilter("Genre", facets.toOptions())

class CategoryFilter(facets: List<Facet>) : SelectFilter("Category", facets.toOptions())

class KeywordFilter(facets: List<Facet>) : SelectFilter("Keyword", facets.toOptions())

class DealsFilter(facets: List<Facet>) : SelectFilter("Deals", facets.toOptions())

class OnSaleFilter(facets: List<Facet>) : SelectFilter("On Sale", facets.toOptions())

private fun List<Facet>.toOptions() = (listOf("All" to "") + map { it.toPair() }).toTypedArray()
