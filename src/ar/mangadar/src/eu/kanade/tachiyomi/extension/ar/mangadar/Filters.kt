package eu.kanade.tachiyomi.extension.ar.mangadar

import eu.kanade.tachiyomi.source.model.Filter

internal class SortFilter(name: String, private val pairs: Array<Pair<String, String>>) : Filter.Select<String>(name, pairs.map { it.first }.toTypedArray()) {
    val selected get() = pairs[state].second
}

internal class TypeFilter(name: String, private val pairs: Array<Pair<String, String>>) : Filter.Select<String>(name, pairs.map { it.first }.toTypedArray()) {
    val selected get() = pairs[state].second
}

internal class StatusFilter(name: String, private val pairs: Array<Pair<String, String>>) : Filter.Select<String>(name, pairs.map { it.first }.toTypedArray()) {
    val selected get() = pairs[state].second
}

internal class GenreFilter(name: String, private val pairs: Array<Pair<String, String>>) : Filter.Select<String>(name, pairs.map { it.first }.toTypedArray()) {
    val selected get() = pairs[state].second
}

internal val sortingList = arrayOf(
    Pair("الأخيرة", "latest"),
    Pair("الأكثر شعبية", "popular"),
)

internal val typeList = arrayOf(
    Pair("الكل", ""),
    Pair("مانغا", "manga"),
    Pair("مانهوا", "manhwa"),
    Pair("مانهوا صيني", "manhua"),
)

internal val statusList = arrayOf(
    Pair("الكل", ""),
    Pair("مستمر", "ongoing"),
    Pair("مكتمل", "completed"),
)

internal val genreFilterList = arrayOf(
    Pair("الكل", ""),
    Pair("أكشن", "action"),
    Pair("مغامرة", "adventure"),
    Pair("كوميديا", "comedy"),
    Pair("دراما", "drama"),
    Pair("خيال", "fantasy"),
    Pair("رعب", "horror"),
    Pair("رومانسي", "romance"),
    Pair("شونين", "shounen"),
    Pair("سينين", "seinen"),
    Pair("مدرسي", "school"),
    Pair("خارق للطبيعة", "supernatural"),
    Pair("تاريخي", "historical"),
    Pair("إيسيكاي", "isekai"),
    Pair("تناسخ", "reincarnation"),
    Pair("نفسي", "psychological"),
    Pair("تشويق", "suspense"),
    Pair("خيال حضري", "urban-fantasy"),
    Pair("الفنون القتالية", "martial-arts"),
    Pair("سفر عبر الزمن", "time-travel"),
    Pair("غموض", "mystery"),
    Pair("حائز على جوائز", "award-winning"),
    Pair("شريرة", "villainess"),
    Pair("خيال علمي", "sci-fi"),
    Pair("بقاء", "survival"),
    Pair("شريحة من الحياة", "slice-of-life"),
)
