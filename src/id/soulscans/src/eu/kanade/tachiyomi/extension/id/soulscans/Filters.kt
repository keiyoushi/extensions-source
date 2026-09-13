package eu.kanade.tachiyomi.extension.id.soulscans

import eu.kanade.tachiyomi.source.model.Filter

sealed class SelectFilter(
    name: String,
    private val options: List<Pair<String, String>>,
) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val selected get() = options[state].second

    class Status(options: List<Pair<String, String>>) : SelectFilter("Status", options)
    class Genre(options: List<Pair<String, String>>) : SelectFilter("Genre", options)
    class Type(options: List<Pair<String, String>>) : SelectFilter("Type", options)
    class Colored(options: List<Pair<String, String>>) : SelectFilter("Colored", options)
    class Format(options: List<Pair<String, String>>) : SelectFilter("Format", options)
    class Sort(options: List<Pair<String, String>>) : SelectFilter("Sort", options)
    class Order(options: List<Pair<String, String>>) : SelectFilter("Order", options)
}

sealed class TextFilter(name: String) : Filter.Text(name) {
    class Author : TextFilter("Author")
    class Artist : TextFilter("Artist")
    class Publisher : TextFilter("Publisher")
}
