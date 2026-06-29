package eu.kanade.tachiyomi.extension.all.simplyhentai

import eu.kanade.tachiyomi.source.model.Filter

class SortFilter(values: Array<String> = labels) : Filter.Select<String>("Sort by", values) {
    val orders = arrayOf("", "upload-date", "popularity")

    companion object {
        val labels = arrayOf("Relevance", "Upload Date", "Popularity")
    }
}

class SeriesFilter : Filter.Text("Series") {
    inline val value: String?
        get() = state.ifBlank { null }
}

class TagsFilter : Filter.Text("Tags") {
    inline val value: List<String>?
        get() = if (state.isBlank()) null else state.split(',')
}

class ArtistsFilter : Filter.Text("Artists") {
    inline val value: List<String>?
        get() = if (state.isBlank()) null else state.split(',')
}

class TranslatorsFilter : Filter.Text("Translators") {
    inline val value: List<String>?
        get() = if (state.isBlank()) null else state.split(',')
}

class CharactersFilter : Filter.Text("Characters") {
    inline val value: List<String>?
        get() = if (state.isBlank()) null else state.split(',')
}

class Note(type: String) : Filter.Header("Separate multiple $type with commas (,)")
