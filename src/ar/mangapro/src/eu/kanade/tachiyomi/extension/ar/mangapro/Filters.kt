package eu.kanade.tachiyomi.extension.ar.mangapro

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

class TriStateFilter(name: String, val value: String) : Filter.TriState(name)

abstract class TriStateGroupFilter(
    name: String,
    options: List<Pair<String, String>>,
) : Filter.Group<TriStateFilter>(
    name,
    options.map { TriStateFilter(it.first, it.second) },
) {
    val included get() = state.filter { it.isIncluded() }.map { it.value }
    val excluded get() = state.filter { it.isExcluded() }.map { it.value }
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
            "أحدث السلاسل" to "latest",
            "أحدث الفصول" to "latest_chapter",
            "الأكثر شهرة" to "popular",
            "الشعبية الإجمالية" to "total_popularity",
            "الأقدم" to "oldest",
            "أبجدي (أ-ي)" to "az",
            "أبجدي (ي-أ)" to "za",
        ),
    )

class StatusFilter :
    SelectFilter<String?>(
        name = "الحالة",
        options = listOf(
            "جميع الحالات" to null,
            "مستمر" to "مستمر",
            "مكتمل" to "مكتمل",
            "متوقف" to "متوقف",
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

class GenreFilter :
    TriStateGroupFilter(
        name = "التصنيف",
        options = genres,
    )

val genres = listOf(
    "أكشن" to "Action",
    "للكبار" to "Adult",
    "مغامرة" to "Adventure",
    "كوميديا" to "Comedy",
    "الدوجينشي" to "Doujinshi",
    "دراما" to "Drama",
    "إتشي" to "Ecchi",
    "خيال" to "Fantasy",
    "تحوّل الجنس" to "Gender Bender",
    "حريم" to "Harem",
    "هنتاي" to "Hentai",
    "تاريخي" to "Historical",
    "رعب" to "Horror",
    "جوسي" to "Josei",
    "لوليكون" to "Lolicon",
    "فنون القتال" to "Martial Arts",
    "الناضج" to "Mature",
    "الميكا" to "Mecha",
    "الميلف" to "Milf",
    "نفسي" to "Psychological",
    "رومانسي" to "Romance",
    "حياة المدرسة" to "School Life",
    "الخيال العلمي" to "Sci-fi",
    "السينين" to "Seinen",
    "شوتاكون" to "Shotacon",
    "الشوجو" to "Shoujo",
    "الشوجو آي" to "Shoujo Ai",
    "الشونين" to "Shounen",
    "الشونين آي" to "Shounen Ai",
    "شريحة من الحياة" to "Slice of Life",
    "فاحش" to "Smut",
    "الرياضة" to "Sports",
    "خارق للطبيعة" to "Supernatural",
    "المأساة" to "Tragedy",
    "ياوي" to "Yaoi",
    "يوري" to "Yuri",
)
