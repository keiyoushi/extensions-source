package eu.kanade.tachiyomi.extension.en.cloudrecess

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

object CloudRecessFilters {
    open class QueryPartFilter(
        displayName: String,
        val vals: Array<Pair<String, String>>,
    ) : Filter.Select<String>(
        displayName,
        vals.map { it.first }.toTypedArray(),
    ) {
        fun toQueryPart() = vals[state].second
    }

    private inline fun <reified R> FilterList.asQueryPart(): String {
        return (first { it is R } as QueryPartFilter).toQueryPart()
    }

    open class CheckBoxFilterList(name: String, val items: List<String>) :
        Filter.Group<Filter.CheckBox>(name, items.map(::CheckBoxVal))

    private class CheckBoxVal(name: String) : Filter.CheckBox(name, false)

    private inline fun <reified R> FilterList.checkedItems(): List<String> {
        return (first { it is R } as CheckBoxFilterList).state
            .filter { it.state }
            .map { it.name }
    }

    internal class TypeFilter : QueryPartFilter("Type", FiltersData.TYPE_LIST)
    internal class StatusFilter : QueryPartFilter("Status", FiltersData.STATUS_LIST)

    internal class GenresFilter : CheckBoxFilterList("Genres", FiltersData.GENRES_LIST)

    val FILTER_LIST get() = FilterList(
        TypeFilter(),
        StatusFilter(),
        GenresFilter(),
    )

    data class FilterSearchParams(
        val type: String = "",
        val status: String = "",
        val genres: List<String> = emptyList(),
    )

    internal fun getSearchParameters(filters: FilterList): FilterSearchParams {
        if (filters.isEmpty()) return FilterSearchParams()

        return FilterSearchParams(
            filters.asQueryPart<TypeFilter>(),
            filters.asQueryPart<StatusFilter>(),
            filters.checkedItems<GenresFilter>(),
        )
    }

    private object FiltersData {
        val TYPE_LIST = arrayOf(
            Pair("All Types", ""),
            Pair("Manga", "manga"),
            Pair("Manhwa", "manhwa"),
            Pair("OEL/Original", "oel"),
            Pair("One Shot", "one-shot"),
            Pair("Webtoon", "webtoon"),
        )

        val STATUS_LIST = arrayOf(
            Pair("All Status", ""),
            Pair("Cancelled", "cancelled"),
            Pair("Completed", "completed"),
            Pair("Hiatus", "hiatus"),
            Pair("Ongoing", "ongoing"),
            Pair("Pending", "pending"),
        )

        val GENRES_LIST = listOf(
            "3P Relationship/s",
            "Action",
            "Adventure",
            "Age Gap",
            "Amnesia/Memory Loss",
            "Art/s or Creative/s",
            "BL",
            "Bloody",
            "Boss/Employee",
            "Childhood Friend/s",
            "Comedy",
            "Coming of Age",
            "Contractual Relationship",
            "Crime",
            "Cross Dressing",
            "Crush",
            "Depraved",
            "Drama",
            "Enemies to Lovers",
            "Family Life",
            "Fantasy",
            "Fetish",
            "First Love",
            "Food",
            "Friends to Lovers",
            "Fxckbuddy",
            "GL",
            "Games",
            "Guideverse",
            "Hardcore",
            "Harem",
            "Historical",
            "Horror",
            "Idols/Celeb/Showbiz",
            "Infidelity",
            "Intense",
            "Isekai",
            "Josei",
            "Light Hearted",
            "Living Together",
            "Love Triangle",
            "Love/Hate",
            "Manipulative",
            "Master/Servant",
            "Mature",
            "Military",
            "Music",
            "Mystery",
            "Nameverse",
            "Obsessive",
            "Omegaverse",
            "On Campus/College Life",
            "One Sided Love",
            "Part Timer",
            "Photography",
            "Psychological",
            "Rebirth/Reincarnation",
            "Red Light",
            "Retro",
            "Revenge",
            "Rich Kids",
            "Romance",
            "Royalty/Nobility/Gentry",
            "SM/BDSM/SUB-DOM",
            "School Life",
            "Sci-Fi",
            "Self-Discovery",
            "Shounen Ai",
            "Slice of Life",
            "Smut",
            "Sports",
            "Step Family",
            "Supernatural",
            "Teacher/Student",
            "Thriller",
            "Tragedy",
            "Tsundere",
            "Uncensored",
            "Violence",
            "Voyeur",
            "Work Place/Office Workers",
            "Yakuza/Gangsters",
        )
    }
}
