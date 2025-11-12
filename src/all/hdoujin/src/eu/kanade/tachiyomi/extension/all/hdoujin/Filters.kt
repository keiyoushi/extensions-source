
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(): FilterList {
    return FilterList(
        SelectFilter("Sort by", getSortsList),
        CategoryFilter("Categories"),
        Filter.Separator(),
        TagType("Tags Include Type", "i"),
        TagType("Tags Exclude Type", "e"),
        Filter.Separator(),
        Filter.Header("Separate tags with commas (,)"),
        Filter.Header("Prepend with dash (-) to exclude"),
        TextFilter("Tags", "tag"),
        TextFilter("Male Tags", "male"),
        TextFilter("Female Tags", "female"),
        TextFilter("Mixed Tags", "mixed"),
        TextFilter("Other Tags", "other"),
        Filter.Separator(),
        TextFilter("Artists", "artist"),
        TextFilter("Parodies", "parody"),
        TextFilter("Characters", "character"),
        Filter.Separator(),
        TextFilter("Uploader", "reason"),
        TextFilter("Circles", "circle"),
        TextFilter("Languages", "language"),
        Filter.Separator(),
        Filter.Header("Filter by pages, for example: (>20)"),
        TextFilter("Pages", "pages"),
    )
}

class CheckBoxFilter(name: String, val value: Int, state: Boolean) : Filter.CheckBox(name, state)

internal class CategoryFilter(name: String) :
    Filter.Group<CheckBoxFilter>(
        name,
        listOf(
            Pair("Manga", 2),
            Pair("Doujinshi", 4),
            Pair("Illustration", 8),
        ).map { CheckBoxFilter(it.first, it.second, true) },
    )

internal class TagType(title: String, val type: String) : Filter.Select<String>(
    title,
    arrayOf("AND", "OR"),
)

internal open class TextFilter(name: String, val type: String) : Filter.Text(name)

internal open class SelectFilter(name: String, val vals: List<Pair<String, String>>, state: Int = 2) :
    Filter.Select<String>(name, vals.map { it.first }.toTypedArray(), state) {
    val selected get() = vals[state].second.takeIf { it.isNotEmpty() }
}

private val getSortsList: List<Pair<String, String>> = listOf(
    Pair("Title", "2"),
    Pair("Pages", "3"),
    Pair("Date", ""),
    Pair("Views", "8"),
    Pair("Favourites", "9"),
    Pair("Popular This Week", "popular"),
)
