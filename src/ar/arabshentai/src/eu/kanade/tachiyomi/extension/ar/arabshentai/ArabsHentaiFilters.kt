package eu.kanade.tachiyomi.extension.ar.arabshentai

import eu.kanade.tachiyomi.source.model.Filter

class StatusFilter :
    Filter.Group<FilterCheckbox>(
        "الحالة",
        arrayOf(
            Pair("مستمرة", "on-going"),
            Pair("مكتملة", "end"),
            Pair("ملغية", "canceled"),
            Pair("متوقفة حالياً", "on-hold"),
        ).map { FilterCheckbox(it.first, it.second) },
    )

internal var genreList: List<Pair<String, String>> = emptyList()

class FilterCheckbox(name: String, val uriPart: String) : Filter.CheckBox(name)

class GenresFilter :
    Filter.Group<FilterCheckbox>("التصنيفات", genreList.map { FilterCheckbox(it.first, it.second) })

class GenresOpFilter : UriPartFilter(
    "شرط التصنيفات",
    arrayOf(
        Pair("يحتوي على إحدى التصنيفات المدرجة", ""),
        Pair("يحتوي على جميع التصنيفات المدرجة", "1"),
    ),
)

open class UriPartFilter(displayName: String, private val pairs: Array<Pair<String, String>>) :
    Filter.Select<String>(displayName, pairs.map { it.first }.toTypedArray()) {
    fun toUriPart() = pairs[state].second
}
