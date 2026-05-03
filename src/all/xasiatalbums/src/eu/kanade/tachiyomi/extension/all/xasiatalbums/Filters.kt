package eu.kanade.tachiyomi.extension.all.xasiatalbums

import eu.kanade.tachiyomi.source.model.Filter

open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}

val initialCategories = mapOf(
    "None" to "",
    "Cosplay" to "albums/cosplay",
    "Japanese" to "albums/japanese",
    "Korean" to "albums/korean",
    "Graphis" to "albums/graphis",
    "Xiuren" to "albums/xiuren",
    "Lovepop" to "albums/lovepop",
    "JVID" to "albums/jvid",
    "Artgravia" to "albums/artgravia",
    "Patreon" to "albums/patreon",
    "Djawva" to "albums/djawva",
    "Fantia" to "albums/fantia",
    "Gals" to "albums/gals",
    "Photobook" to "albums/photobook",
    "Chinese & Taiwan" to "albums/categories/china-taiwan",
    "JAV & AV Models" to "albums/categories/jav",
    "Gravure Idols" to "albums/categories/gravure-idols",
)
