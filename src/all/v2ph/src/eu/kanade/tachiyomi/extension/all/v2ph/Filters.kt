package eu.kanade.tachiyomi.extension.all.v2ph

import eu.kanade.tachiyomi.source.model.Filter

open class UriPartFilter(
    displayName: String,
    private val vals: Array<Pair<String, String>>,
) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}

class CategoryFilter :
    UriPartFilter(
        "Category",
        arrayOf(
            "None" to "",
            "Sexy" to "sexy-girls",
            "Goddess" to "nvshen",
            "Short hair" to "short-hair",
            "Pure" to "pure",
            "Lingerie" to "underwear-beauty",
            "Magazine" to "magazine",
            "Sexy Models" to "glamour-models",
            "Legs" to "beautiful-legs",
            "Japanese" to "japanese-girls",
            "Quality" to "best-quality",
            "Outside" to "outside",
            "Bikini" to "bikini-girls",
        ),
    )

class CountryFilter :
    UriPartFilter(
        "Country",
        arrayOf(
            "None" to "",
            "China" to "china",
            "Japan" to "japan",
            "Korea" to "south-korea",
            "Taiwan" to "taiwan",
            "Thailand" to "thailand",
            "Europe and America" to "europe",
        ),
    )
