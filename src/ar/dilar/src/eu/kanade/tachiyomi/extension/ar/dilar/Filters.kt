package eu.kanade.tachiyomi.extension.ar.dilar

import eu.kanade.tachiyomi.source.model.Filter

class TriStateOption(name: String, val value: String) : Filter.TriState(name)

class CheckBoxOption(name: String, val value: String) : Filter.CheckBox(name)

open class TriStateGroup(name: String, options: List<Pair<String, String>>) : Filter.Group<TriStateOption>(name, options.map { TriStateOption(it.first, it.second) }) {
    val included get() = state.filter { it.isIncluded() }.map { it.value }
    val excluded get() = state.filter { it.isExcluded() }.map { it.value }
}

open class CheckBoxGroup(name: String, options: List<Pair<String, String>>) : Filter.Group<CheckBoxOption>(name, options.map { CheckBoxOption(it.first, it.second) }) {
    val checked get() = state.filter { it.state }.map { it.value }
}

class TypeFilter :
    TriStateGroup(
        "نوع السلسلة",
        listOf(
            "مانها" to "3",
            "مانجا" to "1",
            "مانهوا (كوريا)" to "2",
            "عربي" to "4",
            "دوجينشي" to "6",
            "روسي" to "8",
            "اندونيسي" to "7",
            "كوميك" to "5",
        ),
    )

class CategoryFilter(name: String, categories: List<Pair<String, String>>) : TriStateGroup(name, categories)

class StoryStatusFilter :
    CheckBoxGroup(
        "حالة القصة",
        listOf(
            "مستمر" to "ongoing",
            "مكتمل" to "completed",
            "متوقف" to "hiatus",
            "ملغية" to "cancelled",
        ),
    )

class TranslationStatusFilter :
    CheckBoxGroup(
        "حالة الترجمة",
        listOf(
            "مستمر" to "ongoing",
            "مكتمل" to "completed",
            "متوقف" to "hiatus",
            "ملغية" to "cancelled",
            "مرخصة" to "licensed",
        ),
    )

class OneshotFilter : Filter.CheckBox("ون شوت")

class Over17Filter :
    Filter.Select<String>(
        "17+",
        arrayOf("الكل", "+17 فقط", "بدون +17"),
    ) {
    fun toValue() = arrayOf(null, true, false)[state]
}
