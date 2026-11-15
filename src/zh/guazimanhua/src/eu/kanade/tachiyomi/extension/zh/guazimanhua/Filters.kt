package eu.kanade.tachiyomi.extension.zh.guazimanhua

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun buildFilterList() = FilterList(
    Filter.Header("筛选条件（应用后需重新搜索）"),
    GenreFilter(),
    RegionFilter(),
    AudienceFilter(),
    StatusFilter(),
    SortFilter(),
)

// cid param
class GenreFilter :
    Filter.Select<String>(
        "分类",
        arrayOf(
            "全部", "耽美", "恋爱", "校园", "霸总", "都市", "穿越", "古风", "玄幻", "奇幻",
            "科幻", "灵异", "动作", "悬疑", "冒险", "搞笑", "热血", "恐怖", "系统", "逆袭",
            "脑洞", "复仇", "真人", "其它",
        ),
    ) {
    override fun toString() = arrayOf(
        "0", "41", "9", "29", "5", "42", "8", "23", "25", "31", "22", "21",
        "54", "11", "30", "15", "13", "14", "148", "97", "55", "61", "17", "27",
    )[state]
}

// city param
class RegionFilter :
    Filter.Select<String>(
        "地区",
        arrayOf("全部", "大陆", "欧美", "港台", "日韩", "国漫"),
    ) {
    override fun toString() = arrayOf("0", "42", "43", "77", "78", "338")[state]
}

// audience param
class AudienceFilter :
    Filter.Select<String>(
        "受众",
        arrayOf("全部", "男频", "女频"),
    ) {
    override fun toString() = arrayOf("0", "1", "2")[state]
}

// is_end param (1=completed, 2=ongoing)
class StatusFilter :
    Filter.Select<String>(
        "进度",
        arrayOf("全部", "连载中", "已完结"),
    ) {
    override fun toString() = arrayOf("0", "2", "1")[state]
}

// sort param; defaults to "人气" (popularity) to match the popular list
class SortFilter :
    Filter.Select<String>(
        "排序",
        arrayOf("今日热门", "人气", "更新", "评分"),
        1,
    ) {
    override fun toString() = arrayOf("daily", "hits", "update", "score")[state]
}
