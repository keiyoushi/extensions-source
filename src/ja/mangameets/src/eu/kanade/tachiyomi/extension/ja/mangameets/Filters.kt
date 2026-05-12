package eu.kanade.tachiyomi.extension.ja.mangameets

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import keiyoushi.utils.firstInstanceOrNull
import okhttp3.HttpUrl.Builder

open class SelectFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    val value: String
        get() = vals[state].second
}

class SortFilter :
    SelectFilter(
        "Sort By",
        arrayOf(
            Pair("All", ""),
            Pair("新着順", "last_episode_published_at"),
            Pair("閲覧数順", "total_view_count"),
            Pair("スキ！数順", "loves_count"),
            Pair("担当希望数順", "editor_requests_count"),
        ),
    )

class TagGenreFilter(entries: List<Pair<String, String>>) :
    SelectFilter(
        "Tags / Genres",
        (listOf(Pair("All", "")) + entries).toTypedArray(),
    )

internal fun Builder.addTagGenreFilter(filters: FilterList) {
    filters.firstInstanceOrNull<TagGenreFilter>()?.value?.takeIf { it.isNotBlank() }?.let {
        val (param, name) = it.split(":", limit = 2)
        addQueryParameter(param, name)
    }
}

internal fun Builder.addFilter(param: String, filter: SelectFilter) = filter.value.takeIf { it.isNotBlank() }?.let { addQueryParameter(param, it) }
