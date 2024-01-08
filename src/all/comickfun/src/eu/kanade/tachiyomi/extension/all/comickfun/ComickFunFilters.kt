package eu.kanade.tachiyomi.extension.all.comickfun

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(): FilterList {
    return FilterList(
        Filter.Header(name = "The filter is ignored when using text search."),
        GenreFilter("Genre", getGenresList),
        DemographicFilter("Demographic", getDemographicList),
        TypeFilter("Type", getTypeList),
        SortFilter("Sort", getSortsList),
        StatusFilter("Status", getStatusList),
        CompletedFilter("Completely Scanlated?"),
        CreatedAtFilter("Created at", getCreatedAtList),
        MinimumFilter("Minimum Chapters"),
        Filter.Header("From Year, ex: 2010"),
        FromYearFilter("From"),
        Filter.Header("To Year, ex: 2021"),
        ToYearFilter("To"),
        Filter.Header("Separate tags with commas"),
        TagFilter("Tags"),
    )
}

/** Filters **/
internal class GenreFilter(name: String, genreList: List<Pair<String, String>>) :
    Filter.Group<TriFilter>(name, genreList.map { TriFilter(it.first, it.second) })

internal class TagFilter(name: String) : TextFilter(name)

internal class DemographicFilter(name: String, demographicList: List<Pair<String, String>>) :
    Filter.Group<TriFilter>(name, demographicList.map { TriFilter(it.first, it.second) })

internal class TypeFilter(name: String, typeList: List<Pair<String, String>>) :
    Filter.Group<CheckBoxFilter>(name, typeList.map { CheckBoxFilter(it.first, it.second) })

internal class CompletedFilter(name: String) : CheckBoxFilter(name)

internal class CreatedAtFilter(name: String, createdAtList: List<Pair<String, String>>) :
    SelectFilter(name, createdAtList)

internal class MinimumFilter(name: String) : TextFilter(name)

internal class FromYearFilter(name: String) : TextFilter(name)

internal class ToYearFilter(name: String) : TextFilter(name)

internal class SortFilter(name: String, sortList: List<Pair<String, String>>, state: Int = 0) :
    SelectFilter(name, sortList, state)

internal class StatusFilter(name: String, statusList: List<Pair<String, String>>, state: Int = 0) :
    SelectFilter(name, statusList, state)

/** Generics **/
internal open class TriFilter(name: String, val value: String) : Filter.TriState(name)

internal open class TextFilter(name: String) : Filter.Text(name)

internal open class CheckBoxFilter(name: String, val value: String = "") : Filter.CheckBox(name)

internal open class SelectFilter(name: String, private val vals: List<Pair<String, String>>, state: Int = 0) :
    Filter.Select<String>(name, vals.map { it.first }.toTypedArray(), state) {
    fun getValue() = vals[state].second
}

/** Filters Data **/
private val getGenresList: List<Pair<String, String>> = listOf(
    Pair("4-Koma", "4-koma"),
    Pair("Action", "action"),
    Pair("Adaptation", "adaptation"),
    Pair("Adult", "adult"),
    Pair("Adventure", "adventure"),
    Pair("Aliens", "aliens"),
    Pair("Animals", "animals"),
    Pair("Anthology", "anthology"),
    Pair("Award Winning", "award-winning"),
    Pair("Comedy", "comedy"),
    Pair("Cooking", "cooking"),
    Pair("Crime", "crime"),
    Pair("Crossdressing", "crossdressing"),
    Pair("Delinquents", "delinquents"),
    Pair("Demons", "demons"),
    Pair("Doujinshi", "doujinshi"),
    Pair("Drama", "drama"),
    Pair("Ecchi", "ecchi"),
    Pair("Fan Colored", "fan-colored"),
    Pair("Fantasy", "fantasy"),
    Pair("Full Color", "full-color"),
    Pair("Gender Bender", "gender-bender"),
    Pair("Genderswap", "genderswap"),
    Pair("Ghosts", "ghosts"),
    Pair("Gore", "gore"),
    Pair("Gyaru", "gyaru"),
    Pair("Harem", "harem"),
    Pair("Historical", "historical"),
    Pair("Horror", "horror"),
    Pair("Incest", "incest"),
    Pair("Isekai", "isekai"),
    Pair("Loli", "loli"),
    Pair("Long Strip", "long-strip"),
    Pair("Mafia", "mafia"),
    Pair("Magic", "magic"),
    Pair("Magical Girls", "magical-girls"),
    Pair("Martial Arts", "martial-arts"),
    Pair("Mature", "mature"),
    Pair("Mecha", "mecha"),
    Pair("Medical", "medical"),
    Pair("Military", "military"),
    Pair("Monster Girls", "monster-girls"),
    Pair("Monsters", "monsters"),
    Pair("Music", "music"),
    Pair("Mystery", "mystery"),
    Pair("Ninja", "ninja"),
    Pair("Office Workers", "office-workers"),
    Pair("Official Colored", "official-colored"),
    Pair("Oneshot", "oneshot"),
    Pair("Philosophical", "philosophical"),
    Pair("Police", "police"),
    Pair("Post-Apocalyptic", "post-apocalyptic"),
    Pair("Psychological", "psychological"),
    Pair("Reincarnation", "reincarnation"),
    Pair("Reverse Harem", "reverse-harem"),
    Pair("Romance", "romance"),
    Pair("Samurai", "samurai"),
    Pair("School Life", "school-life"),
    Pair("Sci-Fi", "sci-fi"),
    Pair("Sexual Violence", "sexual-violence"),
    Pair("Shota", "shota"),
    Pair("Shoujo Ai", "shoujo-ai"),
    Pair("Shounen Ai", "shounen-ai"),
    Pair("Slice of Life", "slice-of-life"),
    Pair("Smut", "smut"),
    Pair("Sports", "sports"),
    Pair("Superhero", "superhero"),
    Pair("Supernatural", "supernatural"),
    Pair("Survival", "survival"),
    Pair("Thriller", "thriller"),
    Pair("Time Travel", "time-travel"),
    Pair("Traditional Games", "traditional-games"),
    Pair("Tragedy", "tragedy"),
    Pair("User Created", "user-created"),
    Pair("Vampires", "vampires"),
    Pair("Video Games", "video-games"),
    Pair("Villainess", "villainess"),
    Pair("Virtual Reality", "virtual-reality"),
    Pair("Web Comic", "web-comic"),
    Pair("Wuxia", "wuxia"),
    Pair("Yaoi", "yaoi"),
    Pair("Yuri", "yuri"),
    Pair("Zombies", "zombies"),
)

private val getDemographicList: List<Pair<String, String>> = listOf(
    Pair("Shounen", "1"),
    Pair("Shoujo", "2"),
    Pair("Seinen", "3"),
    Pair("Josei", "4"),
)

private val getTypeList: List<Pair<String, String>> = listOf(
    Pair("Manga", "jp"),
    Pair("Manhwa", "kr"),
    Pair("Manhua", "cn"),
)

private val getCreatedAtList: List<Pair<String, String>> = listOf(
    Pair("", ""),
    Pair("3 days", "3"),
    Pair("7 days", "7"),
    Pair("30 days", "30"),
    Pair("3 months", "90"),
    Pair("6 months", "180"),
    Pair("1 year", "365"),
)

private val getSortsList: List<Pair<String, String>> = listOf(
    Pair("Most popular", "follow"),
    Pair("Most follows", "user_follow_count"),
    Pair("Most views", "view"),
    Pair("High rating", "rating"),
    Pair("Last updated", "uploaded"),
    Pair("Newest", "created_at"),
)

private val getStatusList: List<Pair<String, String>> = listOf(
    Pair("All", "0"),
    Pair("Ongoing", "1"),
    Pair("Completed", "2"),
    Pair("Cancelled", "3"),
    Pair("Hiatus", "4"),
)
