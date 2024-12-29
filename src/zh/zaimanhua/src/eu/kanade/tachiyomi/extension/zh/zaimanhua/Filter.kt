package eu.kanade.tachiyomi.extension.zh.zaimanhua

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

open class QueryFilter(name: String, private val key: String, private val params: Array<Pair<String, String>>) :
    Filter.Select<String>(name, params.map { it.first }.toTypedArray()) {
    fun addQuery(builder: HttpUrl.Builder) {
        val param = params[state].second
        if (param.isNotEmpty()) {
            builder.addQueryParameter(key, param)
        }
    }
}
class RankingGroup : Filter.Group<Filter<*>>(
    "排行榜（搜索文本时无效）",
    listOf<Filter<*>>(
        TimeFilter(),
        SortFilter(),
    ),
) {
    private class TimeFilter : QueryFilter(
        "榜单",
        "by_time",
        arrayOf(
            Pair("日排行", "0"),
            Pair("周排行", "1"),
            Pair("月排行", "2"),
            Pair("总排行", "3"),
        ),
    )

    private class SortFilter : QueryFilter(
        "排序",
        "rank_type",
        arrayOf(
            Pair("人气", "0"),
            Pair("吐槽", "1"),
            Pair("订阅", "2"),
        ),
    )
}
