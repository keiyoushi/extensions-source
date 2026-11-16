package eu.kanade.tachiyomi.extension.zh.bookwalkertw

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters() = FilterList(
    SortFilter(),
    CategoryFilter(),
    SerialFilter(),
    AllAgesFilter(),
)

class SortFilter :
    SelectFilter(
        "Sort by",
        arrayOf(
            "人氣度" to "rank",
            "發售日新至舊" to "sell_desc",
            "發售日舊至新" to "sell_asc",
            "價格低至高" to "price_asc",
            "價格高至低" to "price_desc",
        ),
    )

class CategoryFilter :
    SelectFilter(
        "Category",
        arrayOf(
            "全部漫畫" to null,
            "少年漫畫" to "19",
            "少女漫畫" to "20",
            "青年漫畫" to "21",
            "淑女漫畫" to "22",
            "BL漫畫" to "23",
            "百合漫畫" to "24",
            "兒童漫畫" to "25",
            "漫畫雜誌" to "26",
            "畫集．設定集" to "27",
            "日文書" to "69",
            "同人誌" to "78",
            "其他" to "91",
        ),
    )

class SerialFilter : Filter.CheckBox("連載")

class AllAgesFilter : Filter.CheckBox("全年齡")

open class SelectFilter(name: String, private val options: Array<Pair<String, String?>>) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val value: String?
        get() = options[state].second
}
