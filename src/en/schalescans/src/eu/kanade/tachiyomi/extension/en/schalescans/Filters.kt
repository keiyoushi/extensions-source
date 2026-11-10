package eu.kanade.tachiyomi.extension.en.schalescans

import eu.kanade.tachiyomi.source.model.Filter

open class SelectFilter(name: String, private val options: List<Pair<String, String>>, state: Int = 0) : Filter.Select<String>(name, options.map { it.first }.toTypedArray(), state) {
    val selected: String?
        get() = options[state].second.takeIf { it.isNotEmpty() }
}

class SiteFilter(showNsfw: Boolean) :
    SelectFilter(
        "Site",
        listOf(
            "All" to "",
            "Main site" to "false",
            "R18 site" to "true",
        ),
        if (showNsfw) 0 else 1,
    )

class RatingCheckBox(name: String, val value: String, state: Boolean) : Filter.CheckBox(name, state)

class RatingFilter(showNsfw: Boolean) :
    Filter.Group<RatingCheckBox>(
        "Content rating",
        listOf(
            RatingCheckBox("Safe", "safe", true),
            RatingCheckBox("Suggestive", "suggestive", true),
            RatingCheckBox("Erotica", "erotica", showNsfw),
            RatingCheckBox("Pornographic", "pornographic", showNsfw),
        ),
    ) {
    val selected: List<String>?
        get() = state.filter { it.state }.map { it.value }.takeIf { it.size < state.size }
}

class StatusFilter :
    SelectFilter(
        "Status",
        listOf(
            "All" to "",
            "Ongoing" to "Ongoing",
            "Completed" to "Completed",
            "Oneshot" to "Oneshot",
            "Hiatus" to "Hiatus",
            "Dropped" to "Dropped",
        ),
    )

class GenreFilter(genres: List<String>) : SelectFilter("Genre", listOf("All" to "") + genres.map { it.toTitleCase() to it }.sortedBy { it.first })

class SortFilter :
    SelectFilter(
        "Sort by",
        listOf(
            "Latest release" to "",
            "Recently added" to "created_at.desc",
            "A to Z" to "title.asc",
        ),
    )
