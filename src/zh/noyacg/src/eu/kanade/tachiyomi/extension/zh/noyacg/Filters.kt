package eu.kanade.tachiyomi.extension.zh.noyacg

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.FormBody

fun getFilterListInternal() = FilterList(
    SearchTypeFilter(),
    SortFilter(),
    StatusFilter(),
)

interface SearchFilter {
    fun addTo(builder: FormBody.Builder)
}

class SearchTypeFilter :
    Filter.Select<String>("搜索类型", arrayOf("预设", "标签", "作者")),
    SearchFilter {
    override fun addTo(builder: FormBody.Builder) {
        builder.addEncoded("mode", arrayOf("default", "tag", "author")[state])
    }
}

class SortFilter :
    Filter.Select<String>("排序方式", arrayOf("预设", "观看次数", "收藏", "评分")),
    SearchFilter {
    override fun addTo(builder: FormBody.Builder) {
        builder.addEncoded("sort", arrayOf("", "views", "favorites", "rating")[state])
    }
}

class StatusFilter :
    Filter.Select<String>("完结状态", arrayOf("全部", "连载中", "已完结")),
    SearchFilter {
    override fun addTo(builder: FormBody.Builder) {
        builder.addEncoded("finished", arrayOf("", "false", "true")[state])
    }
}
