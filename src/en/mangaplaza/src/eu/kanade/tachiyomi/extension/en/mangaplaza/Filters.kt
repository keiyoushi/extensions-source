package eu.kanade.tachiyomi.extension.en.mangaplaza

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl.Builder

open class SelectFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    val value: String
        get() = vals[state].second
}

fun Builder.addPathFilter(segment: String, filter: SelectFilter?) = filter?.value?.takeIf(String::isNotEmpty)?.let { addPathSegment(segment).addPathSegment(it) }

class SortFilter :
    SelectFilter(
        "Sort by",
        arrayOf(
            "Most Recommended" to "recommend",
            "Popularity" to "rank",
            "Newest" to "new",
            "Price: Low to High" to "price_low",
            "Price: High to Low" to "price_high",
            "Highest Rated" to "review_point",
            "Most Reviews" to "review_cnt",
        ),
    )

class GenreFilter(entries: List<Pair<String, String>>) :
    SelectFilter(
        "Genre",
        (listOf("All" to "") + entries).toTypedArray(),
    )

class TagFilter(entries: List<Pair<String, String>>) :
    SelectFilter(
        "Tag",
        (listOf("All" to "") + entries).toTypedArray(),
    )
