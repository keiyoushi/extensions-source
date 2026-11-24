package eu.kanade.tachiyomi.extension.all.ahrigallery

import eu.kanade.tachiyomi.source.model.Filter

open class SelectFilter<T>(name: String, private val options: List<Pair<String, T>>) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val selected get() = options[state].second
}

class CategoryFilter :
    SelectFilter<String>(
        "Category",
        listOf(
            "All" to "0",
            "Doujinshi" to "4",
            "Manga" to "6",
            "Artist CG" to "3",
            "Game CG" to "8",
            "Western" to "2",
            "Non-H" to "5",
            "Image Set" to "1",
            "Misc" to "7",
        ),
    )

class SortFilter :
    SelectFilter<Pair<String, String>>(
        "Sort",
        listOf(
            "From New To Old" to ("default" to "DESC"),
            "From Old to New" to ("default" to "ASC"),
            "Popular Collection" to ("fav" to "DESC"),
            "Comic Score" to ("rating" to "DESC"),
            "Number of Ratings" to ("nrating" to "DESC"),
        ),
    )
