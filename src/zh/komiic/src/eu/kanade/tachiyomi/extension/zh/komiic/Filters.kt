package eu.kanade.tachiyomi.extension.zh.komiic

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun buildFilterList(): FilterList {
    val categories = mapOf(
        "1" to "愛情", "3" to "神鬼", "4" to "校園", "5" to "搞笑", "6" to "生活",
        "7" to "懸疑", "8" to "冒險", "10" to "職場", "11" to "魔幻", "2" to "後宮",
        "12" to "魔法", "13" to "格鬥", "14" to "宅男", "15" to "勵志", "16" to "耽美",
        "17" to "科幻", "18" to "百合", "19" to "治癒", "20" to "萌系", "21" to "熱血",
        "22" to "競技", "23" to "推理", "24" to "雜誌", "25" to "偵探", "26" to "偽娘",
        "27" to "美食", "9" to "恐怖", "28" to "四格", "31" to "社會", "32" to "歷史",
        "33" to "戰爭", "34" to "舞蹈", "35" to "武俠", "36" to "機戰", "37" to "音樂",
        "40" to "體育", "42" to "黑道", "46" to "腐女", "47" to "異世界", "48" to "驚悚",
        "51" to "成人", "54" to "戰鬥", "55" to "復仇", "56" to "轉生", "57" to "黑暗奇幻",
        "58" to "戲劇", "59" to "生存", "60" to "策略", "61" to "政治", "62" to "黑暗",
        "64" to "動作", "70" to "性轉換", "73" to "色情", "181" to "校园", "78" to "日常",
        "81" to "青春", "83" to "料理", "85" to "醫療", "86" to "致鬱", "87" to "心理",
        "88" to "穿越", "92" to "友情", "93" to "犯罪", "97" to "劇情",
        "110" to "運動", "113" to "少女", "114" to "賭博", "119" to "情色", "123" to "女性向",
        "128" to "性轉", "129" to "溫馨", "164" to "同人",
    )
    return FilterList(
        Filter.Header("過濾條件（搜索關鍵字時無效）"),
        CategoryFilter(categories),
        StatusFilter(),
        SortFilter(),
    )
}

class Category(val id: String, name: String) : Filter.CheckBox(name)

class CategoryFilter(categories: Map<String, String>) :
    Filter.Group<Category>("類型（篩選同時包含全部所選標簽的漫畫）", categories.map { Category(it.key, it.value) }) {
    val selected get() = state.filter(Category::state).map(Category::id)
}

class StatusFilter : Filter.Select<String>("狀態", arrayOf("全部", "連載", "完結")) {
    val value get() = arrayOf("", "ONGOING", "END")[state]
}

class SortFilter : Filter.Select<String>("排序", arrayOf("更新", "觀看數", "喜愛數")) {
    val value get() = arrayOf("DATE_UPDATED", "VIEWS", "FAVORITE_COUNT")[state]
}
