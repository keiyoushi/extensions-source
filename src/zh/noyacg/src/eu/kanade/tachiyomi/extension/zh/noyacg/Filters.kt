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
    Filter.Select<String>("類型", arrayOf("預設", "標籤", "作者")),
    SearchFilter {
    override fun addTo(builder: FormBody.Builder) {
        builder.addEncoded("mode", arrayOf("default", "tag", "author")[state])
    }
}

class SortFilter :
    Filter.Select<String>("排序方式", arrayOf("預設", "觀看次數", "收藏", "評分")),
    SearchFilter {
    override fun addTo(builder: FormBody.Builder) {
        builder.addEncoded("sort", arrayOf("", "views", "favorites", "rating")[state])
    }
}

class StatusFilter :
    Filter.Select<String>("完結狀態", arrayOf("全部", "連載中", "已完結")),
    SearchFilter {
    override fun addTo(builder: FormBody.Builder) {
        builder.addEncoded("finished", arrayOf("", "false", "true")[state])
    }
}
