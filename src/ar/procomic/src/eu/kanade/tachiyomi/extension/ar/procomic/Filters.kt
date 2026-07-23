package eu.kanade.tachiyomi.extension.ar.procomic

import eu.kanade.tachiyomi.source.model.Filter
import java.util.Calendar

abstract class SelectFilter<T>(
    name: String,
    private val options: List<Pair<String, T>>,
) : Filter.Select<String?>(
    name,
    options.map { it.first }.toTypedArray(),
) {
    val selected get() = options[state].second
}

class TypeFilter :
    SelectFilter<String?>(
        name = "النوع",
        options = listOf(
            "الكل" to null,
            "مانجا" to "manga",
            "مانها" to "manhua",
            "مانهوا" to "manhwa",
        ),
    )

class SortFilter :
    SelectFilter<String>(
        name = "الفرز",
        options = listOf(
            "الأكثر شهرة" to "popular",
            "أحدث السلاسل" to "latest",
            "أحدث الفصول" to "latest_chapter",
            "الشعبية الإجمالية" to "total_popularity",
            "الأقدم" to "oldest",
            "أبجدي (أ-ي)" to "az",
            "أبجدي (ي-أ)" to "za",
        ),
    )

private val currentYear = Calendar.getInstance()[Calendar.YEAR]

class YearFilter :
    SelectFilter<String?>(
        name = "السنة",
        options = buildList {
            add("جميع السنوات" to null)
            (currentYear downTo 1970).mapTo(this) { it.toString() to it.toString() }
        },
    )
