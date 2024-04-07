package eu.kanade.tachiyomi.extension.en.comicfans

import eu.kanade.tachiyomi.source.model.Filter

open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
    Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}

inline fun <reified R> List<*>.getUriPart(): String? =
    (filterIsInstance<R>().first() as UriPartFilter).toUriPart().takeIf { it.isNotEmpty() }

class GenreFilter : UriPartFilter(
    "Genre",
    arrayOf(
        Pair("All", ""),
        Pair("BL", "1001"),
        Pair("Fantasy", "1002"),
        Pair("GL", "1003"),
        Pair("CEO", "1004"),
        Pair("Romance", "1005"),
        Pair("Harem", "1006"),
        Pair("Action", "1007"),
        Pair("Teen", "1008"),
        Pair("Adventure", "1009"),
        Pair("Eastern", "1010"),
        Pair("Comedy", "1011"),
        Pair("Esports", "1012"),
        Pair("Historical", "1013"),
        Pair("Mystery", "1014"),
        Pair("Modern", "1015"),
        Pair("Urban", "1016"),
        Pair("Wuxia", "1017"),
        Pair("Suspense", "1018"),
        Pair("Female Lead", "1019"),
        Pair("Western Fantasy", "1020"),
        Pair("Horror", "1022"),
        Pair("Realistic Fiction", "1023"),
        Pair("Cute", "1024"),
        Pair("Campus", "1025"),
        Pair("Sci-fi", "1026"),
        Pair("History", "1027"),
    ),
)

class LastUpdateFilter : UriPartFilter(
    "Last Update",
    arrayOf(
        Pair("All", ""),
        Pair("Within 3 Days", "3"),
        Pair("Within 7 Days", "7"),
        Pair("Within 15 Days", "15"),
        Pair("Within 30 Days", "30"),
    ),
)

class StatusFilter : UriPartFilter(
    "Status",
    arrayOf(
        Pair("All", ""),
        Pair("Ongoing", "0"),
        Pair("Completed", "1"),
    ),
)
