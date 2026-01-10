package eu.kanade.tachiyomi.extension.all.comicklive

import eu.kanade.tachiyomi.source.model.Filter
import java.util.Calendar
import kotlin.collections.filter

abstract class SelectFilter(
    name: String,
    private val options: List<Pair<String, String>>,
) : Filter.Select<String>(
    name,
    options.map { it.first }.toTypedArray(),
) {
    val selected get() = options[state].second.takeIf { it.isNotEmpty() }
}

class CheckBoxFilter(name: String, val value: String) : Filter.CheckBox(name)

abstract class CheckBoxGroup(
    name: String,
    options: List<Pair<String, String>>,
) : Filter.Group<CheckBoxFilter>(
    name,
    options.map { CheckBoxFilter(it.first, it.second) },
) {
    val checked get() = state.filter { it.state }.map { it.value }
}

class TriStateFilter(name: String, val slug: String) : Filter.TriState(name)

abstract class TriStateGroupFilter(
    name: String,
    options: List<Pair<String, String>>,
) : Filter.Group<TriStateFilter>(
    name,
    options.map { TriStateFilter(it.first, it.second) },
) {
    val included get() = state.filter { it.isIncluded() }.map { it.slug }
    val excluded get() = state.filter { it.isExcluded() }.map { it.slug }
}

private val getSortsList = listOf(
    "Latest" to "created_at",
    "Popular" to "user_follow_count",
    "Highest Rating" to "rating",
    "Last Uploaded" to "uploaded",
)

class SortFilter : Filter.Sort(
    name = "Sort",
    values = getSortsList.map { it.first }.toTypedArray(),
    state = Selection(0, false),
) {
    val selected get() = state?.let { getSortsList[it.index] }?.second.takeIf { it?.isNotEmpty() ?: false }
}

class GenreFilter(genres: List<Metadata.Name>) : TriStateGroupFilter(
    name = "Genre",
    options = genres.map { it.name to it.slug },
)

class TagFilter(tags: List<Metadata.Name>) : TriStateGroupFilter(
    name = "Tags",
    options = tags.map { it.name to it.slug },
)

class TagFilterText : Filter.Text(
    name = "Tags",
)

class DemographicFilter : CheckBoxGroup(
    name = "Demographic",
    options = listOf(
        "Shounen" to "1",
        "Josei" to "2",
        "Seinen" to "3",
        "Shoujo" to "4",
        "None" to "0",
    ),
)

class CreatedAtFilter : SelectFilter(
    name = "Created At",
    options = listOf(
        "" to "",
        "3 days ago" to "3",
        "7 days ago" to "7",
        "30 days ago" to "30",
        "3 months ago" to "90",
        "6 months ago" to "180",
        "1 year ago" to "365",
        "2 years ago" to "730",
    ),
)

class TypeFilter : CheckBoxGroup(
    name = "Type",
    options = listOf(
        "Manga" to "jp",
        "Manhwa" to "kr",
        "Manhua" to "cn",
        "Others" to "others",
    ),
)

class MinimumChaptersFilter : Filter.Text(
    name = "Minimum Chapters",
)

class StatusFilter : SelectFilter(
    name = "Status",
    options = listOf(
        "" to "",
        "Ongoing" to "1",
        "Completed" to "2",
        "Cancelled" to "3",
        "Hiatus" to "4",
    ),
)

class ContentRatingFilter : SelectFilter(
    name = "Content Rating",
    options = listOf(
        "" to "",
        "Safe" to "safe",
        "Suggestive" to "suggestive",
        "Erotica" to "erotica",
    ),
)

class ReleaseFrom : SelectFilter(
    name = "Release From",
    options = buildList {
        add(("" to ""))
        Calendar.getInstance().get(Calendar.YEAR).downTo(1990).mapTo(this) {
            ("$it" to it.toString())
        }
        add(("Before 1990" to "0"))
    },
)

class ReleaseTo : SelectFilter(
    name = "Release To",
    options = buildList {
        add(("" to ""))
        Calendar.getInstance().get(Calendar.YEAR).downTo(1990).mapTo(this) {
            ("$it" to it.toString())
        }
        add(("Before 1990" to "0"))
    },
)
