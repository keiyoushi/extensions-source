package eu.kanade.tachiyomi.extension.all.webtoons

import eu.kanade.tachiyomi.source.model.Filter

abstract class SelectFilter(
    name: String,
    private val options: List<Pair<String, String?>>,
) : Filter.Select<String>(
    name,
    options.map { it.first }.toTypedArray(),
) {
    val selected get() = options[state].second
}

class SearchType :
    SelectFilter(
        name = "Type",
        options = listOf(
            "All" to null,
            "Originals" to "originals",
            "Canvas" to "canvas",
        ),
    )

class UpdateDayFilter :
    SelectFilter(
        name = "Update day (Originals)",
        options = listOf("Any" to null) +
            listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
                .map { it to it.lowercase() } +
            ("Completed" to "complete"),
    )

class Genre(val name: String, val originals: String?, val canvas: String?) {
    val label get() = when {
        originals == null -> "$name (Canvas only)"
        canvas == null -> "$name (Originals only)"
        else -> name
    }
}

class GenreFilter(private val genres: List<Genre>) :
    Filter.Select<String>(
        "Genre",
        (listOf("Any") + genres.map { it.label }).toTypedArray(),
    ) {
    val selected get() = genres.getOrNull(state - 1)
}

class SortFilter :
    SelectFilter(
        name = "Sort",
        options = listOf(
            "Popularity" to "MANA",
            "Likes" to "LIKEIT",
            "Date updated" to "UPDATE",
        ),
    )
