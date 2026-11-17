package eu.kanade.tachiyomi.extension.vi.sanyteam

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

class SortFilter :
    Filter.Sort(
        "Sắp xếp",
        OPTIONS.map { it.first }.toTypedArray(),
    ) {
    val orderBy get() = state?.let { OPTIONS[it.index].second }

    val order get() = if (state?.ascending == true) "asc" else "desc"

    companion object {
        private val OPTIONS = listOf(
            "Mới cập nhật" to "modified",
            "Truyện mới" to "date",
            "Bình luận" to "comment_count",
            "Tên truyện" to "title",
        )
    }
}

fun getFilters() = FilterList(SortFilter())
