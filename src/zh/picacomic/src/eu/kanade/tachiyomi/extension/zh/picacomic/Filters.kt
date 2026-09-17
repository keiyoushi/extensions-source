package eu.kanade.tachiyomi.extension.zh.picacomic

import eu.kanade.tachiyomi.source.model.Filter

abstract class UriPartFilter(
    displayName: String,
    val vals: List<Pair<String, String>>,
    defaultValue: Int = 0,
) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray(), defaultValue) {
    fun toUriPart() = vals[state].second
}

internal class SortFilter :
    UriPartFilter(
        "排序",
        listOf(
            "新到旧" to "dd",
            "旧到新" to "da",
            "最多爱心" to "ld",
            "最多绅士指名" to "vd",
        ),
    )

internal class CategoryFilter :
    UriPartFilter(
        "类型",
        listOf("全部" to "") + listOf(
            "大家都在看", "牛牛不哭", "那年今天", "官方都在看",
            "嗶咔漢化", "全彩", "長篇", "同人", "短篇", "圓神領域",
            "碧藍幻想", "CG雜圖", "純愛", "百合花園", "後宮閃光", "單行本", "姐姐系",
            "妹妹系", "SM", "人妻", "NTR", "強暴",
            "艦隊收藏", "Love Live", "SAO 刀劍神域", "Fate",
            "東方", "禁書目錄", "Cosplay",
            "英語 ENG", "生肉", "性轉換", "足の恋", "非人類",
            "耽美花園", "偽娘哲學", "扶他樂園", "重口地帶", "歐美", "WEBTOON",
        ).map { it to it },
    )

internal class RankFilter :
    UriPartFilter(
        "榜单",
        listOf(
            "无" to "",
            "过去24小时最热门" to "/comics/leaderboard?tt=H24&ct=VC",
            "过去7天最热门" to "/comics/leaderboard?tt=D7&ct=VC",
            "过去30天最热门" to "/comics/leaderboard?tt=D30&ct=VC",
        ),
    )

internal class FavouriteFilter : Filter.CheckBox("收藏列表")
