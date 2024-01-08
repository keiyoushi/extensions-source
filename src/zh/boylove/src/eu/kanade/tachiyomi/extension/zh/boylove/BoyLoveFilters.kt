package eu.kanade.tachiyomi.extension.zh.boylove

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

/*
 * 1-0-2-1-1-0-1-0
 * [0] cate, useless
 * [1] tag(genre), 0=all
 * [2] done(status), 2=all, 0=ongoing, 1=completed
 * [3] order(sort), 1=normal
 * [4] page, index from 1
 * [5] type, 0=all, 1=清水, 2=有肉
 * [6] 1=manga, 2=novel, else=manga
 * [7] vip, 0=default, useless
 */
internal fun parseFilters(page: Int, filters: FilterList): String {
    var status = '2'
    var type = '0'
    var genre = "0"
    var sort = '1'
    for (filter in filters) {
        when (filter) {
            is StatusFilter -> status = STATUS_KEYS[filter.state]
            is TypeFilter -> type = TYPE_KEYS[filter.state]
            is GenreFilter -> if (filter.state > 0) genre = filter.values[filter.state]
            is SortFilter -> sort = SORT_KEYS[filter.state]
            else -> {}
        }
    }
    return "1-$genre-$status-$sort-$page-$type-1-0"
}

internal class StatusFilter : Filter.Select<String>("状态", STATUS_NAMES)

private val STATUS_NAMES = arrayOf("全部", "连载中", "已完结")
private val STATUS_KEYS = arrayOf('2', '0', '1')

internal class TypeFilter : Filter.Select<String>("类型", TYPE_NAMES)

private val TYPE_NAMES = arrayOf("全部", "清水", "有肉")
private val TYPE_KEYS = arrayOf('0', '1', '2')

internal class GenreFilter(names: Array<String>) : Filter.Select<String>("标签", names)

internal class SortFilter : Filter.Select<String>("排序", SORT_NAMES)

private val SORT_NAMES = arrayOf("顺序", "类似排行榜")
private val SORT_KEYS = arrayOf('1', '2')
