package eu.kanade.tachiyomi.multisrc.colorlibanime

import eu.kanade.tachiyomi.source.model.Filter

open class UriPartFilter(
    displayName: String,
    private val vals: Array<Pair<String, String>>,
    state: Int = 0,
) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray(), state) {
    fun toUriPart() = vals[state].second
}

class OrderFilter(state: Int = 0) :
    UriPartFilter(
        "Order By",
        arrayOf(
            Pair("A-Z", "default"),
            Pair("Updated", "updated"),
            Pair("New", "published"),
            Pair("Views", "view"),
        ),
        state,
    )
