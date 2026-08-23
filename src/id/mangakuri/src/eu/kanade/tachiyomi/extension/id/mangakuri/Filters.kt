package eu.kanade.tachiyomi.extension.id.mangakuri

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

abstract class SelectFilter(
    displayName: String,
    private val vals: List<Pair<String, String>>,
) : Filter.Select<String>(
    displayName,
    vals.map { it.first }.toTypedArray(),
) {
    fun selectedValue() = vals[state].second
}

val sortOptions = arrayOf(
    "New" to "new",
    "Top Views" to "views",
    "Top Rate" to "rate",
    "Top Bookmark" to "bookmark",
    "Title A-Z" to "az",
    "Title Z-A" to "za",
)

class SortFilter(
    selection: Selection = Selection(0, false),
) : Filter.Sort("Sort By", sortOptions.map { it.first }.toTypedArray(), selection) {

    val selected get() = sortOptions[state!!.index].second
    val order get() = if (state!!.ascending) "asc" else "desc"

    companion object {
        val LATEST = FilterList(SortFilter(Selection(0, false)))
        val POPULAR = FilterList(SortFilter(Selection(1, false)))
    }
}

class StatusFilter :
    SelectFilter(
        "Status",
        listOf(
            "All" to "",
            "Ongoing" to "ONGOING",
            "Completed" to "COMPLETED",
            "Hiatus" to "HIATUS",
        ),
    )

class TypeFilter :
    SelectFilter(
        "Type",
        listOf(
            "All" to "",
            "Manga" to "MANGA",
            "Manhwa" to "MANHWA",
            "Manhua" to "MANHUA",
        ),
    )

class ColorFilter :
    SelectFilter(
        "Color",
        listOf(
            "All" to "",
            "Full Color" to "FULL_COLOR",
            "B&W" to "BW",
        ),
    )

class ReadingFilter :
    SelectFilter(
        "Reading",
        listOf(
            "All" to "",
            "Vertical Scroll" to "VERTICAL_SCROLL",
            "Page" to "PAGE",
        ),
    )

class GenreFilter(genres: Map<String, String>) :
    SelectFilter(
        "Genre",
        listOf("All" to "") + genres.toList(),
    )

class TextFilter(name: String, val queryKey: String) : Filter.Text(name)
