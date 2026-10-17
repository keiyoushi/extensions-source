package eu.kanade.tachiyomi.extension.ar.procomic

import eu.kanade.tachiyomi.source.model.Filter
import java.time.Year

abstract class SelectFilter<T>(
    name: String,
    private val options: List<Pair<String, T>>,
) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val selected get() = options[state].second
}

class TypeFilter :
    SelectFilter<String?>(
        "النوع",
        listOf(
            "الكل" to null,
            "مانجا" to "manga",
            "مانها (صينية)" to "manhua",
            "مانهوا (كورية)" to "manhwa",
        ),
    )

class StatusFilter :
    SelectFilter<String?>(
        "الحالة",
        listOf(
            "الكل" to null,
            "مستمر" to "مستمر",
            "مكتمل" to "مكتمل",
            "متوقف" to "متوقف",
        ),
    )

class YearFilter :
    SelectFilter<String?>(
        "السنة",
        buildList {
            add("الكل" to null)
            (Year.now().value downTo 1970).forEach { add(it.toString() to it.toString()) }
        },
    )

class SortFilter :
    SelectFilter<String>(
        "الترتيب",
        listOf(
            "الأكثر شهرة" to "popular",
            "أحدث السلاسل" to "latest",
            "أحدث الفصول" to "latest_chapter",
            "الشعبية الإجمالية" to "total_popularity",
            "الأقدم" to "oldest",
            "أبجدي (أ-ي)" to "az",
            "أبجدي (ي-أ)" to "za",
        ),
    )
