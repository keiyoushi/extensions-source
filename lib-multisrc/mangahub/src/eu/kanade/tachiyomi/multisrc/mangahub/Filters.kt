package eu.kanade.tachiyomi.multisrc.mangahub

import eu.kanade.tachiyomi.source.model.Filter

open class SelectFilter(name: String, private val options: List<Pair<String, String>>) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val selected get() = options[state].second
}

class GenreFilter(val genres: List<ApiGenre>) : SelectFilter("Genre", listOf("All" to "all") + genres.map { it.title to it.slug })

class HideNsfwFilter : Filter.CheckBox("Hide NSFW")

class HideYaoiFilter : Filter.CheckBox("Hide Yaoi")

class OrderFilter :
    SelectFilter(
        "Order",
        listOf(
            "Popular" to "POPULAR",
            "Updates" to "LATEST",
            "A-Z" to "ALPHABET",
            "New" to "NEW",
            "Completed" to "COMPLETED",
        ),
    )
