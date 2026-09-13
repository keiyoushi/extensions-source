package eu.kanade.tachiyomi.extension.zh.hanabimanga

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun buildFilterList() = FilterList(
    Filter.Header("筛选条件（搜寻关键字时无效）"),
    CategoryFilter(),
    SortFilter(),
    RegionFilter(),
    StatusFilter(),
)

class CategoryFilter :
    Filter.Select<String>(
        "分类",
        arrayOf(
            "全部", "推理", "后宫", "科幻", "百合", "恐怖", "恋爱", "音乐", "校园",
            "穿越", "战斗", "运动", "武侠", "奇幻", "惊悚", "搞笑", "日常", "悬疑",
            "冒险", "历史", "乙女", "美食", "职场", "玄幻", "机战", "魔幻", "伪娘",
        ),
    ) {
    override fun toString(): String = arrayOf(
        "", "1", "2", "3", "4", "5", "6", "7", "8",
        "9", "10", "11", "12", "13", "14", "15", "16", "17",
        "18", "19", "20", "21", "22", "23", "24", "26", "27",
    )[state]
}

class RegionFilter : Filter.Select<String>("分区", arrayOf("全部", "日漫", "韩漫", "美漫", "其他")) {
    override fun toString(): String = arrayOf("", "jp", "kr", "us", "others")[state]
}

class SortFilter : Filter.Select<String>("排序", arrayOf("默认", "评分最高", "最近更新", "最新上架")) {
    override fun toString(): String = arrayOf(
        "id.asc.nullslast",
        "rating_average.desc.nullslast",
        "updated_at.desc.nullslast",
        "created_at.desc.nullslast",
    )[state]
}

class StatusFilter : Filter.Select<String>("状态", arrayOf("全部", "连载中", "已完结")) {
    override fun toString() = arrayOf("", "false", "true")[state]
}
