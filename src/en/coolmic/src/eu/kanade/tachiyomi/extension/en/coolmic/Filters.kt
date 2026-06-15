package eu.kanade.tachiyomi.extension.en.coolmic

import eu.kanade.tachiyomi.source.model.Filter

class SortFilter :
    SelectFilter(
        "Sort by",
        arrayOf(
            "Relevance" to "_score+desc,+like_vote_count+desc",
            "Recently Added" to "start_at+desc",
            "Oldest" to "start_at+asc",
            "Popular" to "like_vote_count+desc",
            "Explicitness" to "erotic_rating_average+desc,+like_vote_count+desc",
        ),
    )

class StatusFilter :
    SelectFilter(
        "Content Rating",
        arrayOf(
            "All" to "",
            "All Ages" to "is_mature '0'",
            "Mature (18+)" to "is_mature '1'",
            "Uncensored" to "is_uncensored '1'",
        ),
    )

open class SelectFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    val value: String
        get() = vals[state].second
}
