package eu.kanade.tachiyomi.extension.ko.toonkor

import eu.kanade.tachiyomi.source.model.Filter

open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun isSelection(name: String): Boolean = name == vals[state].first
    fun toUriPart() = vals[state].second
}

class TypeFilter :
    UriPartFilter(
        "Type",
        arrayOf(
            Pair("Webtoons", "/%EC%9B%B9%ED%88%B0"),
            Pair("Manga", "/%EB%8B%A8%ED%96%89%EB%B3%B8"),
            Pair("Hentai", "/%EB%A7%9D%EA%B0%80"),
        ),
    )

class SortFilter :
    UriPartFilter(
        "Sort",
        arrayOf(
            Pair("Popular", ""),
            Pair("Latest", "?fil=%EC%B5%9C%EC%8B%A0"),
            Pair("Completed", "/%EC%99%84%EA%B2%B0"),
        ),
    )
