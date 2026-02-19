package eu.kanade.tachiyomi.extension.zh.bilimanga

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun buildFilterList() = FilterList(
    Filter.Header("篩選條件（搜尋關鍵字時無效）"),
    ThemeFilter(), // 1
    TypeFilter(), // 5
    RegionFilter(), // 4
    SortFilter(), // 0
    AnimeFilter(), // 3
    NovelFilter(), // 7
    StatusFilter(), // 2
    TimeFilter(), // 6
)

class ThemeFilter :
    Filter.Select<String>(
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
            "超自然",
        ),
    ) {
    override fun toString(): String = arrayOf(
        "0", "1", "2", "3", "4", "5", "6", "7", "8",
        "9", "10", "11", "12", "13", "14", "15", "16",
        "17", "18", "19", "20", "21", "22", "23", "24",
        "25", "26", "27", "28", "29", "30", "31", "32",
        "33", "34", "35", "36", "37", "38", "39", "40",
        "41", "42", "43", "44", "45", "46", "47", "48",
        "49", "50", "51", "52", "53", "54", "55", "56",
        "57", "58", "59", "60", "61", "62", "63", "64",
        "65",
    )[state]
}

class TypeFilter :
    Filter.Select<String>(
        "作品分類",
        arrayOf(
            "全部",
            "奇幻冒險", "戰鬥熱血", "懸疑驚悚", "校園青春",
            "愛情浪漫", "職場都市", "歷史文化", "科幻未來",
            "奇異幻想", "治癒溫馨", "末日生存", "其他分類",
        ),
    ) {
    override fun toString(): String = arrayOf(
        "0", "1", "2", "3", "4", "5", "6", "7", "8",
        "9", "10", "11", "12",
    )[state]
}

class RegionFilter :
    Filter.Select<String>(
        "作品地區",
        arrayOf("不限", "日本", "韓國", "港台", "歐美", "大陸"),
    ) {
    override fun toString() = arrayOf("0", "1", "2", "3", "4", "5")[state]
}

class SortFilter :
    Filter.Select<String>(
        "排序方式",
        arrayOf(
            "最近更新", "月點擊", "周推薦", "月推薦", "周鮮花",
            "月鮮花", "字數", "收藏數", "周點擊", "最新入庫",
        ),
    ) {
    override fun toString(): String = arrayOf(
        "lastupdate", "monthvisit", "weekvote", "monthvote", "weekflower",
        "monthflower", "words", "goodnum", "weekvisit", "postdate",
    )[state]
}

class AnimeFilter : Filter.Select<String>("是否動畫", arrayOf("不限", "已動畫化", "未動畫化")) {
    override fun toString() = arrayOf("0", "1", "2")[state]
}

class NovelFilter : Filter.Select<String>("是否輕改", arrayOf("不限", "輕改漫畫", "普通漫畫")) {
    override fun toString() = arrayOf("0", "1", "2")[state]
}

class StatusFilter : Filter.Select<String>("連載狀態", arrayOf("不限", "連載", "完結")) {
    override fun toString() = arrayOf("0", "1", "2")[state]
}

class TimeFilter :
    Filter.Select<String>(
        "更新時間",
        arrayOf("不限", "三日內", "七日內", "半月內", "一月內"),
    ) {
    override fun toString() = arrayOf("0", "1", "2", "3", "4")[state]
}
