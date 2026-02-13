package eu.kanade.tachiyomi.extension.all.manhwa18net

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters() = FilterList(
    Filter.Header("Filters work with both search and browse"),
    Filter.Separator(),
    SortFilter(),
    StatusFilter(),
)

class SortFilter :
    UriPartFilter(
        "Sort By",
        arrayOf(
            "Latest Updates" to "update",
            "Most Viewed" to "top",
            "Most Liked" to "like",
            "Newest" to "new",
        ),
    )

class StatusFilter :
    Filter.Group<StatusCheckBox>(
        "Status",
        listOf(
            StatusCheckBox("Ongoing", "Ongoing"),
            StatusCheckBox("On Hold", "Onhold"),
            StatusCheckBox("Completed", "completed"),
        ),
    )

class StatusCheckBox(name: String, val uriParam: String) : Filter.CheckBox(name)

open class UriPartFilter(
    displayName: String,
    private val vals: Array<Pair<String, String>>,
) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}
