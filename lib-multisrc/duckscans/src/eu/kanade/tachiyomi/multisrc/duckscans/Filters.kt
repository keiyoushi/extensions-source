package eu.kanade.tachiyomi.multisrc.duckscans

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

class SortFilter :
    Filter.Sort(
        "Sort by",
        arrayOf("Views", "Likes", "Release date", "Title"),
        Selection(0, false),
    )

open class SelectFilter(name: String, private val options: Array<String>) : Filter.Select<String>(name, arrayOf("All") + options) {
    val selected: String? get() = options.getOrNull(state - 1)
}

class StatusFilter : SelectFilter("Status", arrayOf("Devam Ediyor", "Güncel", "Tamamlandı", "Yakında", "Durduruldu", "Bırakıldı"))

class TypeFilter : SelectFilter("Type", arrayOf("Manhwa", "Manga", "Manhua"))

class Genre(name: String) : Filter.CheckBox(name)

class GenreFilter(genres: List<String>) : Filter.Group<Genre>("Genres", genres.map(::Genre))

fun getFilters(genres: List<String>?): FilterList {
    val filters = mutableListOf<Filter<*>>(SortFilter(), StatusFilter(), TypeFilter())
    if (!genres.isNullOrEmpty()) {
        filters += Filter.Separator()
        filters += Filter.Header("Series with any of the selected genres")
        filters += GenreFilter(genres)
    }
    return FilterList(filters)
}
