package eu.kanade.tachiyomi.extension.zh.bilibilimanga

import eu.kanade.tachiyomi.source.model.Filter

data class BilibiliTag(val name: String, val id: Int) {
    override fun toString(): String = name
}

open class EnhancedSelect<T>(name: String, values: Array<T>, state: Int = 0) :
    Filter.Select<T>(name, values, state) {

    val selected: T?
        get() = values.getOrNull(state)
}

class GenreFilter(label: String, genres: Array<BilibiliTag>) :
    EnhancedSelect<BilibiliTag>(label, genres)

class AreaFilter(label: String, genres: Array<BilibiliTag>) :
    EnhancedSelect<BilibiliTag>(label, genres)

class SortFilter(label: String, options: Array<BilibiliTag>, state: Int = 0) :
    EnhancedSelect<BilibiliTag>(label, options, state)

class StatusFilter(label: String, statuses: Array<String>) :
    Filter.Select<String>(label, statuses)

class PriceFilter(label: String, prices: Array<String>) :
    Filter.Select<String>(label, prices)
