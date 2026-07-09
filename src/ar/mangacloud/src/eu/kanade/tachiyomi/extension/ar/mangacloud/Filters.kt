package eu.kanade.tachiyomi.extension.ar.mangacloud

import eu.kanade.tachiyomi.source.model.Filter

internal class SortFilter(name: String, private val pairs: Array<Pair<String, String>>) : Filter.Select<String>(name, pairs.map { it.first }.toTypedArray()) {
    val selected get() = pairs[state].second
}

internal class GenreFilter(name: String, private val pairs: Array<Pair<String, String>>) : Filter.Select<String>(name, pairs.map { it.first }.toTypedArray()) {
    val selected get() = pairs[state].second
}

internal class StatusFilter(name: String, private val pairs: Array<Pair<String, String>>) : Filter.Select<String>(name, pairs.map { it.first }.toTypedArray()) {
    val selected get() = pairs[state].second
}
