package eu.kanade.tachiyomi.extension.en.paradisescans

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.HttpUrl

class SortFilter :
    Filter.Select<String>(
        "Sort by",
        SORT_VALUES.map { it.first }.toTypedArray(),
    ) {
    val selectedValue: String
        get() = SORT_VALUES[state].second

    companion object {
        private val SORT_VALUES = listOf(
            "Latest updates" to "latest_chapter",
            "Popular" to "sales",
            "Newest series" to "created_at",
            "Highest rated" to "rating",
            "Title A–Z" to "title",
        )
    }
}

class StatusFilter :
    Filter.Select<String>(
        "Status",
        STATUS_VALUES.map { it.first }.toTypedArray(),
    ) {
    val selectedValue: String
        get() = STATUS_VALUES[state].second

    companion object {
        private val STATUS_VALUES = listOf(
            "All" to "",
            "Ongoing" to "ongoing",
            "Completed" to "completed",
        )
    }
}

class TypeFilter :
    Filter.Select<String>(
        "Format",
        TYPE_VALUES.map { it.first }.toTypedArray(),
    ) {
    val selectedValue: String
        get() = TYPE_VALUES[state].second

    companion object {
        private val TYPE_VALUES = listOf(
            "All" to "",
            "Manga / Manhwa" to "manga",
            "Novels" to "novel",
        )
    }
}

class Genre(name: String) : Filter.CheckBox(name)

class GenreListFilter(genres: List<Genre>) : Filter.Group<Genre>("Genres", genres)

fun getFilters(): FilterList = FilterList(
    SortFilter(),
    StatusFilter(),
    TypeFilter(),
    GenreListFilter(
        listOf(
            "Action", "Adult", "Adventure", "Campus", "Comedy", "Contemporary", "Drama",
            "Ecchi", "Fantasy", "Historical", "Mature", "Mystery", "Omegaverse",
            "Psychological", "Romance", "Sci-Fi", "Seinen", "Shounen Ai", "Smut",
            "Supernatural", "Webtoons", "Workplace", "Yaoi",
        ).map(::Genre),
    ),
)

fun FilterList.applyToUrl(builder: HttpUrl.Builder) {
    forEach { filter ->
        when (filter) {
            is SortFilter -> {
                val sort = filter.selectedValue
                builder.addQueryParameter("sort", sort)
                if (sort == "title") {
                    builder.addQueryParameter("dir", "asc")
                } else if (sort == "sales" || sort == "created_at" || sort == "rating") {
                    builder.addQueryParameter("dir", "desc")
                }
            }
            is StatusFilter -> {
                if (filter.selectedValue.isNotEmpty()) {
                    builder.addQueryParameter("status", filter.selectedValue)
                }
            }
            is TypeFilter -> {
                if (filter.selectedValue.isNotEmpty()) {
                    builder.addQueryParameter("type", filter.selectedValue)
                }
            }
            is GenreListFilter -> {
                val selected = filter.state.filter { it.state }.map { it.name }
                if (selected.isNotEmpty()) {
                    builder.addQueryParameter("genres", selected.joinToString(","))
                }
            }
            else -> {}
        }
    }
}
