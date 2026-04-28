package eu.kanade.tachiyomi.extension.all.xasiatalbums

import eu.kanade.tachiyomi.source.model.Filter

val categories = mutableMapOf(
    "All" to "albums",
    "Gravure Idols" to "albums/categories/gravure-idols",
    "JAV & AV Models" to "albums/categories/jav",
    "South Korea" to "albums/categories/korea",
    "China & Taiwan" to "albums/categories/china-taiwan",
    "Amateur" to "albums/categories/amateur3",
    "Western Girls" to "albums/categories/western-girls",
)

open class UriPartFilter(
    displayName: String,
    private val valuePair: Array<Pair<String, String>>,
) : Filter.Select<String>(displayName, valuePair.map { it.first }.toTypedArray()) {
    fun toUriPart() = valuePair[state].second
}
