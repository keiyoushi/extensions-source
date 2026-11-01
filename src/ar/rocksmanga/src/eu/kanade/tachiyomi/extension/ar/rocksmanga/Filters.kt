package eu.kanade.tachiyomi.extension.ar.rocksmanga

import eu.kanade.tachiyomi.source.model.Filter

open class SelectFilter(name: String, private val options: List<Pair<String, String>>) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val selected get() = options[state].second
}

class SortFilter :
    SelectFilter(
        "ترتيب حسب",
        listOf(
            "آخر التحديثات" to "recently_updated",
            "الأكثر مشاهدة" to "most_viewed",
            "الأعلى تقييماً" to "highest_rated",
            "أبجدياً" to "alphabetical",
            "الأحدث" to "new",
        ),
    )

class TypeFilter :
    SelectFilter(
        "النوع",
        listOf("الكل" to "", "مانجا" to "manga", "مانهوا" to "manhwa", "مانها" to "manhua", "كوميك" to "comic", "رواية" to "novel"),
    )

class StatusFilter :
    SelectFilter(
        "الحالة",
        listOf("الكل" to "", "مستمر" to "releasing", "مكتمل" to "completed", "ملغي" to "canceled", "متوقف" to "on_hold"),
    )

class Genre(name: String, val value: String) : Filter.TriState(name)

class GenreFilter(genres: List<Genre>) : Filter.Group<Genre>("التصنيفات", genres)
