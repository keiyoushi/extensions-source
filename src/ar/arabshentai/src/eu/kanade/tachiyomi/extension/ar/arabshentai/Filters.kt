package eu.kanade.tachiyomi.extension.ar.arabshentai

import eu.kanade.tachiyomi.source.model.Filter

class FilterCheckbox(name: String, val uriPart: String) : Filter.CheckBox(name)

class GenresFilter(genres: List<Pair<String, String>>) : Filter.Group<FilterCheckbox>("التصنيفات", genres.map { FilterCheckbox(it.first, it.second) })

class GenresOpFilter :
    UriPartFilter(
        "شرط التصنيفات",
        arrayOf(
            "يحتوي على إحدى التصنيفات المدرجة" to "",
            "يحتوي على جميع التصنيفات المدرجة" to "1",
        ),
    )

class StatusFilter :
    Filter.Group<FilterCheckbox>(
        "الحالة",
        arrayOf(
            "مستمرة" to "on-going",
            "مكتملة" to "end",
            "ملغية" to "canceled",
            "متوقفة حالياً" to "on-hold",
        ).map { FilterCheckbox(it.first, it.second) },
    )

open class UriPartFilter(displayName: String, private val pairs: Array<Pair<String, String>>) : Filter.Select<String>(displayName, pairs.map { it.first }.toTypedArray()) {
    fun toUriPart() = pairs[state].second
}
