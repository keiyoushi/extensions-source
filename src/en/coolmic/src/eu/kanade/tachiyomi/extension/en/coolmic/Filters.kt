package eu.kanade.tachiyomi.extension.en.coolmic

import eu.kanade.tachiyomi.source.model.Filter

class SortFilter :
    SelectFilter(
        "Sort by",
        arrayOf(
            "Relevance" to "relevance",
            "Recently Added" to "newest",
            "Oldest" to "oldest",
            "Popular" to "like_vote",
            "Explicitness" to "erotic_rating",
        ),
    )

class StatusFilter :
    SelectFilter(
        "Content Rating",
        arrayOf(
            "All" to "",
            "All Ages" to "is_mature:0",
            "Mature (18+)" to "is_mature:1",
            "Uncensored" to "is_uncensored:1",
        ),
    )

open class SelectFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    val value: String
        get() = vals[state].second
}
