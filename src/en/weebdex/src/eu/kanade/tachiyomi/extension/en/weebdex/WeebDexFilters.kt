package eu.kanade.tachiyomi.extension.en.weebdex

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

internal fun buildFilterList() = FilterList(
    Filter.Header("NOTE: Not all filters work on all sources."),
    Filter.Separator(),
    SortFilter(),
    OrderFilter(),
    StatusFilter(),
    LangFilter(),
    DemographicFilter(),
    HasChaptersFilter(),
    Filter.Header("Tags (ignored if searching by text)"),
    TagModeFilter(),
    TagList(WeebDexConstants.tags.keys.toTypedArray()),
    Filter.Header("Tags to exclude (ignored if searching by text)"),
    TagExcludeModeFilter(),
    TagsExcludeFilter(WeebDexConstants.tags.keys.toTypedArray()),
    ContentRatingFilter(),
    YearFromFilter(),
    YearToFilter(),
)

internal class SortFilter : Filter.Select<String>(
    "Sort by",
    WeebDexConstants.sortList.map { it.first }.toTypedArray(),
    0,
) {
    val selected: String
        get() = WeebDexConstants.sortList[state].second
}

internal class OrderFilter : Filter.Select<String>(
    "Order",
    arrayOf("Descending", "Ascending"),
    0,
) {
    val selected: String
        get() = if (state == 0) "desc" else "asc"
}

internal class StatusFilter : Filter.Select<String>(
    "Status",
    WeebDexConstants.statusList.map { it.first }.toTypedArray(),
    0,
) {
    val selected: String?
        get() = WeebDexConstants.statusList[state].second
}

class TagCheckBox(name: String) : Filter.CheckBox(name, false)
class TagList(tags: Array<String>) : Filter.Group<TagCheckBox>("Tags", tags.map { TagCheckBox(it) })
class TagsExcludeFilter(tags: Array<String>) : Filter.Group<TagCheckBox>(
    "Tags to Exclude",
    tags.map { TagCheckBox(it) },
)

class TagModeFilter : Filter.Select<String>(
    "Tag mode",
    arrayOf("AND", "OR"), // what user sees
    0,
) {
    val selected: String
        get() = if (state == 0) "0" else "1" // backend wants 0=AND, 1=OR
}

class TagExcludeModeFilter : Filter.Select<String>(
    "Exclude tag mode",
    arrayOf("OR", "AND"), // what user sees
    0,
) {
    val selected: String
        get() = if (state == 0) "0" else "1" // backend wants 0=OR, 1=AND
}

internal class DemographicFilter : Filter.Select<String>(
    "Demographic",
    WeebDexConstants.demographics.map { it.first }.toTypedArray(),
    0,
) {
    val selected: String?
        get() = WeebDexConstants.demographics[state].second
}

internal class ContentRatingFilter : Filter.Select<String>(
    "Content Rating",
    arrayOf("Any", "Safe", "Suggestive", "Erotica", "Pornographic"),
    0,
) {
    private val apiValues = arrayOf(null, "safe", "suggestive", "erotica", "pornographic")

    val selected: String?
        get() = apiValues[state]
}

internal class LangFilter : Filter.Select<String>(
    "Original Language",
    WeebDexConstants.langList.map { it.first }.toTypedArray(),
    0,
) {
    val query: String?
        get() = WeebDexConstants.langList[state].second
}

internal class HasChaptersFilter : Filter.CheckBox("Has Chapters", true) {
    val selected: String?
        get() = if (state) "1" else null
}

internal class YearFromFilter : Filter.Text("Year (from)")
internal class YearToFilter : Filter.Text("Year (to)")
