package eu.kanade.tachiyomi.extension.en.weebdex

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

val TAGS = mapOf(
    // Formats
    "Oneshot" to "99q3m1plnt",
    "Web Comic" to "1utcekkc70",
    "Doujinshi" to "fnvjk3jg1b",
    "Adaptation" to "pbst9p8bd4",
    "Full Color" to "6amsrv3w16",
    "4-Koma" to "jnqtucy8q3",

    // Genres
    "Action" to "g0eao31zjw",
    "Adventure" to "pjl8oxd1ld",
    "Boys' Love" to "1cnfhxwshb",
    "Comedy" to "onj03z2gnf",
    "Crime" to "bwec51tbms",
    "Drama" to "00xq9oqthh",
    "Fantasy" to "3lhj8r7s6n",
    "Girls' Love" to "i9w6sjikyd",
    "Historical" to "mmf28hr2co",
    "Horror" to "rclreo8b25",
    "Magical Girls" to "hy189x450f",
    "Mystery" to "hv0hsu8kje",
    "Romance" to "o0rm4pweru",
    "Slice of Life" to "13x7xvq10k",
    "Sports" to "zsvyg4whkp",
    "Tragedy" to "85hmqw16y9",

    // Themes
    "Cooking" to "9wm2j2zl1e",
    "Crossdressing" to "arjr4qdpgc",
    "Delinquents" to "h5ioz14hix",
    "Genderswap" to "25k4gcfnfp",
    "Magic" to "evt7r78scn",
    "Monster Girls" to "ddjrvi8vsu",
    "School Life" to "hobsiukk71",
    "Shota" to "lu0sbwbs3r",
    "Supernatural" to "c4rnaci8q6",
    "Traditional Games" to "aqfqkul8rg",
    "Vampires" to "djs29flsq6",
    "Video Games" to "axstzcu7pc",
    "Office Workers" to "6uytt2873o",
    "Martial Arts" to "577a4hd52b",
    "Zombies" to "szg24cwbrm",
    "Survival" to "mt4vdanhfc",
    "Police" to "acai4usl79",
    "Mafia" to "qjuief8bi1",

    // Content Tags
    "Gore" to "hceia50cf9",
    "Sexual Violence" to "xh9k4t31ll",
)

private val DEMOGRAPHICS = listOf(
    "Any" to null,
    "Shounen" to "shounen",
    "Shoujo" to "shoujo",
    "Josei" to "josei",
    "Seinen" to "seinen",
)

private val STATUS_LIST = listOf(
    "Any" to null,
    "Ongoing" to "ongoing",
    "Completed" to "completed",
    "Hiatus" to "hiatus",
    "Cancelled" to "cancelled",
)

private val LANG_LIST = listOf(
    "Any" to null,
    "English" to "en",
    "Japanese" to "ja",
)

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
    TagList(TAGS.keys.toTypedArray()),
    Filter.Header("Tags to exclude (ignored if searching by text)"),
    TagExcludeModeFilter(),
    TagsExcludeFilter(TAGS.keys.toTypedArray()),
    ContentRatingFilter(),
    YearFromFilter(),
    YearToFilter(),
)

internal class SortFilter : Filter.Select<String>(
    "Sort by",
    entries.map { it.displayName }.toTypedArray(),
    0,
) {
    companion object {
        private val entries = listOf(
            Entry("Views", "views"),
            Entry("Updated At", "updatedAt"),
            Entry("Created At", "createdAt"),
            Entry("Chapter Update", "lastUploadedChapterAt"),
            Entry("Title", "title"),
            Entry("Year", "year"),
            Entry("Rating", "rating"),
            Entry("Follows", "follows"),
            Entry("Chapters", "chapters"),
        )
    }

    private data class Entry(val displayName: String, val apiValue: String)

    val selected: String
        get() = entries[state].apiValue
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
    STATUS_LIST.map { it.first }.toTypedArray(),
    0,
) {
    val selected: String?
        get() = STATUS_LIST[state].second
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
    DEMOGRAPHICS.map { it.first }.toTypedArray(),
    0,
) {
    val selected: String?
        get() = DEMOGRAPHICS[state].second
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
    LANG_LIST.map { it.first }.toTypedArray(),
    0,
) {
    val query: String?
        get() = LANG_LIST[state].second
}

internal class HasChaptersFilter : Filter.CheckBox("Has Chapters", true) {
    val selected: String?
        get() = if (state) "1" else null
}

internal class YearFromFilter : Filter.Text("Year (from)")
internal class YearToFilter : Filter.Text("Year (to)")
