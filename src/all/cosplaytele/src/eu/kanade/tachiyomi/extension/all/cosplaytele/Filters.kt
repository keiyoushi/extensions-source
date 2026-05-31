package eu.kanade.tachiyomi.extension.all.cosplaytele

import eu.kanade.tachiyomi.source.model.Filter

class UriPartFilter(
    displayName: String,
    private val valuePair: Array<Pair<String, String>>,
) : Filter.Select<String>(displayName, valuePair.map { it.first }.toTypedArray()) {
    fun toUriPart() = valuePair[state].second
}

val CATEGORIES = arrayOf(
    Pair("All", ""),
    Pair("Cosplay Nude", "category/nude"),
    Pair("Cosplay Ero", "category/no-nude"),
    Pair("Cosplay", "category/cosplay"),
)
