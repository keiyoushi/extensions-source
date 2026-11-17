package eu.kanade.tachiyomi.extension.zh.bilimanga

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun buildFilterList() = FilterList(
    Filter.Header("篩選條件（搜尋關鍵字時無效）"),
    ThemeFilter(),
    TypeFilter(),
    RegionFilter(),
    YearFilter(),
    SortFilter(),
    AnimeFilter(),
    NovelFilter(),
    AwardFilter(),
    StatusFilter(),
    TimeFilter(),
)

open class SiteFilter(name: String, options: Array<String>, private val uriValues: List<String>? = null) : Filter.Select<String>(name, options) {
    val value get() = uriValues?.get(state) ?: state.toString()
}

class ThemeFilter :
    SiteFilter(
        "作品主題",
        arrayOf(
            "不限", "奇幻", "冒險", "異世界", "龍傲天", "魔法",
            "仙俠", "戰爭", "熱血", "戰鬥", "競技", "懸疑",
            "驚悚", "獵奇", "神鬼", "偵探", "校園", "日常",
            "JK", "JC", "青梅竹馬", "妹妹", "大小姐", "女兒",
            "愛情", "耽美", "百合", "NTR", "後宮", "職場",
            "經營", "犯罪", "旅行", "群像", "女性視角",
            "歷史", "武俠", "東方", "勵志", "宅系", "科幻",
            "機戰", "遊戲", "異能", "腦洞", "病嬌", "人外",
            "復仇", "鬥智", "惡役", "間諜", "治癒", "歡樂",
            "萌系", "末日", "大逃殺", "音樂", "美食", "性轉",
            "偽娘", "穿越", "童話", "轉生", "黑暗", "溫馨",
            "超自然", "青春",
        ),
    )

class TypeFilter :
    SiteFilter(
        "作品分類",
        arrayOf(
            "全部",
            "奇幻冒險", "戰鬥熱血", "懸疑驚悚", "校園青春",
            "愛情浪漫", "職場都市", "歷史文化", "科幻未來",
            "奇異幻想", "治癒溫馨", "末日生存", "其他分類",
        ),
    )

class RegionFilter : SiteFilter("作品地區", arrayOf("不限", "日本", "韓國", "港台", "歐美", "大陸"))

class SortFilter :
    SiteFilter(
        "排序方式",
        arrayOf(
            "最近更新", "月點擊", "周點擊", "月推薦", "周推薦",
            "月鮮花", "周鮮花", "字數", "收藏數", "最新入庫",
        ),
        listOf("lastupdate", "monthvisit", "weekvisit", "monthvote", "weekvote", "monthflower", "weekflower", "words", "goodnum", "postdate"),
    )

class AnimeFilter : SiteFilter("是否動畫", arrayOf("不限", "已動畫化", "未動畫化"))

class NovelFilter : SiteFilter("是否輕改", arrayOf("不限", "輕改漫畫", "普通漫畫"))

class StatusFilter : SiteFilter("連載狀態", arrayOf("不限", "連載", "完結"))

class TimeFilter : SiteFilter("更新時間", arrayOf("不限", "三日內", "七日內", "半月內", "一月內"))

class YearFilter :
    SiteFilter(
        "發表年代",
        arrayOf(
            "不限", "2026年", "2025年", "2024年", "2023年", "2022年", "2021年",
            "2020年", "2019年", "2018年", "2017年", "2016年", "2015年", "2014年",
            "2013年", "2012年", "2011年", "2010年", "00年代", "90年代", "80年代", "更早",
        ),
        listOf("0", "2026", "2025", "2024", "2023", "2022", "2021", "2020", "2019", "2018", "2017", "2016", "2015", "2014", "2013", "2012", "2011", "2010", "2000", "1990", "1980", "1970"),
    )

class AwardFilter :
    SiteFilter(
        "這本漫畫真厲害",
        arrayOf(
            "不限", "2027", "2026", "2025", "2024", "2023", "2022", "2021", "2020",
            "2019", "2018", "2017", "2016", "2015", "2014", "2013", "2012", "2011",
            "2010", "2009", "2008", "2007", "2006",
        ),
        listOf("0", "2027", "2026", "2025", "2024", "2023", "2022", "2021", "2020", "2019", "2018", "2017", "2016", "2015", "2014", "2013", "2012", "2011", "2010", "2009", "2008", "2007", "2006"),
    )
