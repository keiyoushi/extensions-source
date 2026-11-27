package eu.kanade.tachiyomi.extension.ko.mangatoki

import eu.kanade.tachiyomi.source.model.Filter

class SearchTypeFilter : SelectFilter("검색 대상", SEARCH_TYPES)

class SortFilter(state: Int = 0) : SelectFilter("정렬", SORTS, state)

open class SelectFilter(name: String, private val options: List<Pair<String, String>>, state: Int = 0) : Filter.Select<String>(name, options.map { it.first }.toTypedArray(), state) {
    val value get() = options[state].second
}

private val SEARCH_TYPES = listOf(
    "제목" to "title",
    "작가" to "artist",
    "캐릭터" to "character",
    "태그" to "tag",
)

val SORTS = listOf(
    "최신순" to "created_at",
    "주간 BEST" to "week",
    "월간 BEST" to "month",
    "조회수순" to "view",
    "오래된순" to "oldest",
)

const val VIEW_SORT = 3
