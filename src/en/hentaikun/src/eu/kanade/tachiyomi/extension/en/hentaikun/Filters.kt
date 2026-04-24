package eu.kanade.tachiyomi.extension.en.hentaikun

import eu.kanade.tachiyomi.source.model.Filter

open class UriPartFilter(
    displayName: String,
    private val vals: Array<Pair<String, String>>,
) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}

class SearchTypeFilter :
    UriPartFilter(
        "Search by",
        arrayOf(
            Pair("Title", "title"),
            Pair("Artist", "artist"),
            Pair("Category", "category"),
            Pair("Translator", "translator"),
        ),
    )
