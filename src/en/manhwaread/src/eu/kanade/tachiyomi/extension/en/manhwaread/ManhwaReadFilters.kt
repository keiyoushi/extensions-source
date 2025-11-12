package eu.kanade.tachiyomi.extension.en.manhwaread

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.Filter.Sort.Selection
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(): FilterList {
    return FilterList(
        SortFilter("Sort by", Selection(0, false), getSortsList),
        GenresFilter("Genres"),
        Filter.Separator(),
        Filter.Header("Separate tags with commas (,)"),
        Filter.Header("Prepend with dash (-) to exclude [ Only for 'Tags' ]"),
        TextFilter("Tags", "manga_tag"),
        Filter.Separator(),
        TextFilter("Authors", "author"),
        TextFilter("Artists", "artist"),
        Filter.Separator(),
        Filter.Header("Filter by year uploaded, for example: (>2024)"),
        UploadedFilter("Uploaded"),
        Filter.Separator(),
        Filter.Header("Filter by chapters, for example: (>20)"),
        PageFilter("Chapters"),
    )
}

internal open class UploadedFilter(name: String) : Filter.Text(name)

internal open class PageFilter(name: String) : Filter.Text(name)

internal open class TextFilter(name: String, val type: String) : Filter.Text(name)

internal class GenresFilter(name: String) :
    Filter.Group<CheckBoxFilter>(
        name,
        listOf(
            "Action" to "650",
            "Adventure" to "645",
            "Comedy" to "536",
            "Drama" to "530",
            "Ecchi" to "537",
            "Fantasy" to "646",
            "Hentai" to "531",
            "Horror" to "590",
            "Isekai" to "2735",
            "Mahou Shoujo" to "2696",
            "Mystery" to "626",
            "Psychological" to "591",
            "Romance" to "538",
            "Sci-Fi" to "688",
            "Slice of Life" to "532",
            "Sports" to "677",
            "Supernatural" to "544",
            "Thriller" to "580",
        ).map { CheckBoxFilter(it.first, it.second, true) },
    )
internal open class CheckBoxFilter(name: String, val value: String, state: Boolean) : Filter.CheckBox(name, state)

internal open class SortFilter(name: String, selection: Selection, private val vals: List<Pair<String, String>>) :
    Filter.Sort(name, vals.map { it.first }.toTypedArray(), selection) {
    fun getValue() = vals[state!!.index].second
}

private val getSortsList: List<Pair<String, String>> = listOf(
    Pair("Latest", "new"),
    Pair("A-Z", "alphabet"),
    Pair("Rating", "rating"),
    Pair("Daily Views", "daily_top"),
    Pair("Weekly Views", "weekly_top"),
    Pair("Monthly Views", "monthly_top"),
    Pair("Yearly Views", "yearly_top"),
    Pair("All Time Views", "all_top"),
)
