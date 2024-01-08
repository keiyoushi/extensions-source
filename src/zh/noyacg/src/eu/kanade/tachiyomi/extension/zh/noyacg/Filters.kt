package eu.kanade.tachiyomi.extension.zh.noyacg

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.FormBody

fun getFilterListInternal() = FilterList(
    Filter.Header("搜索选项"),
    SearchTypeFilter(),
    SortFilter(),
    Filter.Separator(),
    Filter.Header("排行榜（搜索文本时无效）"),
    RankingFilter(),
    RankingRangeFilter(),
)

interface ListingFilter {
    fun addTo(builder: FormBody.Builder)
}

interface SearchFilter : ListingFilter

class SearchTypeFilter : SearchFilter, Filter.Select<String>("搜索范围", arrayOf("综合", "标签", "作者")) {
    override fun addTo(builder: FormBody.Builder) {
        builder.addEncoded("type", arrayOf("de", "tag", "author")[state])
    }
}

class SortFilter : SearchFilter, Filter.Select<String>("排序", arrayOf("时间", "阅读量", "收藏")) {
    override fun addTo(builder: FormBody.Builder) {
        builder.addEncoded("sort", arrayOf("bid", "views", "favorites")[state])
    }
}

class RankingFilter : Filter.Select<String>("排行榜", arrayOf("阅读榜", "收藏榜", "高质量榜")) {
    val path get() = arrayOf("readLeaderboard", "favLeaderboard", "proportion")[state]
}

class RankingRangeFilter : ListingFilter, Filter.Select<String>("阅读/收藏榜范围", arrayOf("日榜", "周榜", "月榜")) {
    override fun addTo(builder: FormBody.Builder) {
        builder.addEncoded("type", arrayOf("day", "week", "moon")[state])
    }
}
