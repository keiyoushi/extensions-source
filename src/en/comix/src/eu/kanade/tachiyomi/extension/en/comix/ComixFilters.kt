package eu.kanade.tachiyomi.extension.en.comix

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.HttpUrl
import java.util.Calendar

class ComixFilters {
    interface UriFilter {
        fun addToUri(builder: HttpUrl.Builder)
    }

    companion object {
        private val currentYear by lazy {
            Calendar.getInstance()[Calendar.YEAR]
        }

        private fun getYearsArray(includeOlder: Boolean): Array<Pair<String, String>> {
            val years = (currentYear downTo 1990).map { it.toString() to it.toString() }
            return if (includeOlder) {
                (years + ("Older" to "older")).toTypedArray()
            } else {
                years.toTypedArray()
            }
        }

        fun getDemographics() = arrayOf(
            Pair("Shoujo", "1"),
            Pair("Shounen", "2"),
            Pair("Josei", "3"),
            Pair("Seinen", "4"),
        )
    }

    fun getFilterList() = FilterList(
        SortFilter(getSortables()),
        StatusFilter(),
        MinChapterFilter(),
        GenreFilter(),
        TypeFilter(),
        DemographicFilter(getDemographics()),
        Filter.Separator(),
        Filter.Header("Release Year"),
        YearFromFilter(),
        YearToFilter(),
    )

    private open class UriPartFilter(
        name: String,
        private val param: String,
        private val vals: Array<Pair<String, String>>,
        defaultValue: String? = null,
    ) : Filter.Select<String>(
        name,
        vals.map { it.first }.toTypedArray(),
        vals.indexOfFirst { it.second == defaultValue }.takeIf { it != -1 } ?: 0,
    ),
        UriFilter {
        override fun addToUri(builder: HttpUrl.Builder) {
            builder.addQueryParameter(param, vals[state].second)
        }
    }

    private open class UriMultiSelectOption(name: String, val value: String) : Filter.CheckBox(name)

    private open class UriMultiSelectFilter(
        name: String,
        private val param: String,
        private val vals: Array<Pair<String, String>>,
    ) : Filter.Group<UriMultiSelectOption>(
        name,
        vals.map { UriMultiSelectOption(it.first, it.second) },
    ),
        UriFilter {
        override fun addToUri(builder: HttpUrl.Builder) {
            val checked = state.filter { it.state }
            checked.forEach {
                builder.addQueryParameter(param, it.value)
            }
        }
    }

    private open class UriTriSelectOption(name: String, val value: String) : Filter.TriState(name)

    private open class UriTriSelectFilter(
        name: String,
        private val param: String,
        private val vals: Array<Pair<String, String>>,
    ) : Filter.Group<UriTriSelectOption>(
        name,
        vals.map { UriTriSelectOption(it.first, it.second) },
    ),
        UriFilter {
        override fun addToUri(builder: HttpUrl.Builder) {
            state.forEach { s ->
                when (s.state) {
                    TriState.STATE_INCLUDE -> builder.addQueryParameter(param, s.value)
                    TriState.STATE_EXCLUDE -> builder.addQueryParameter(param, "!${s.value}")
                }
            }
        }
    }

    private class DemographicFilter(val demographics: Array<Pair<String, String>>) :
        UriTriSelectFilter(
            "Demographic",
            "demographics[]",
            demographics,
        )

    private class TypeFilter : UriMultiSelectFilter(
        "Type",
        "type",
        arrayOf(
            Pair("Manga", "manga"),
            Pair("Manhwa", "manhwa"),
            Pair("Manhua", "manhua"),
            Pair("Other", "other"),
        ),
    )

    private class GenreFilter : UriTriSelectFilter(
        "Genres",
        "genre[]",
        arrayOf(
            Pair("Action", "1"),
            Pair("Adventure", "78"),
            Pair("Avant Garde", "3"),
            Pair("Boys Love", "4"),
            Pair("Comedy", "5"),
            Pair("Demons", "77"),
            Pair("Drama", "6"),
            Pair("Ecchi", "7"),
            Pair("Fantasy", "79"),
            Pair("Girls Love", "9"),
            Pair("Gourmet", "10"),
            Pair("Harem", "11"),
            Pair("Horror", "530"),
            Pair("Isekai", "13"),
            Pair("Iyashikei", "531"),
            Pair("Josei", "15"),
            Pair("Kids", "532"),
            Pair("Magic", "539"),
            Pair("Mahou Shoujo", "533"),
            Pair("Martial Arts", "534"),
            Pair("Mecha", "19"),
            Pair("Military", "535"),
            Pair("Music", "21"),
            Pair("Mystery", "22"),
            Pair("Parody", "23"),
            Pair("Psychological", "536"),
            Pair("Reverse Harem", "25"),
            Pair("Romance", "26"),
            Pair("School", "73"),
            Pair("Sci-Fi", "28"),
            Pair("Seinen", "537"),
            Pair("Shoujo", "30"),
            Pair("Shounen", "31"),
            Pair("Slice of Life", "538"),
            Pair("Space", "33"),
            Pair("Sports", "34"),
            Pair("Super Power", "75"),
            Pair("Supernatural", "76"),
            Pair("Suspense", "37"),
            Pair("Thriller", "38"),
            Pair("Vampire", "39"),
        ),
    )

    private class StatusFilter : UriMultiSelectFilter(
        "Status",
        "status[]",
        arrayOf(
            Pair("Finished", "finished"),
            Pair("Releasing", "releasing"),
            Pair("On Hiatus", "on_hiatus"),
            Pair("Discontinued", "discontinued"),
            Pair("Not Yet Released", "not_yet_released"),
        ),
    )

    private class YearFromFilter : UriPartFilter(
        "From",
        "release_year[from]",
        getYearsArray(includeOlder = true),
        "older",
    )

    private class YearToFilter : UriPartFilter(
        "To",
        "release_year[to]",
        getYearsArray(includeOlder = false),
    )

    private class MinChapterFilter : Filter.Text("Minimum Chapter Length"), UriFilter {
        override fun addToUri(builder: HttpUrl.Builder) {
            if (state.isNotEmpty()) {
                val value = state.toIntOrNull()?.takeIf { it > 0 }
                    ?: throw IllegalArgumentException(
                        "Minimum chapter length must be a positive integer greater than 0",
                    )
                builder.addQueryParameter("min_chap", value.toString())
            }
        }
    }

    private data class Sortable(val title: String, val value: String) {
        override fun toString(): String = title
    }

    private fun getSortables() = arrayOf(
        Sortable("Best Match", "relevance"),
        Sortable("Popular", "views_30d"),
        Sortable("Updated Date", "chapter_updated_at"),
        Sortable("Created Date", "created_at"),
        Sortable("Title", "title"),
        Sortable("Year", "year"),
        Sortable("Total Views", "total_views"),
        Sortable("Most Follows", "followed_count"),
    )

    private class SortFilter(private val sortables: Array<Sortable>) :
        Filter.Sort(
            "Sort By",
            sortables.map(Sortable::title).toTypedArray(),
            Selection(1, false),
        ),
        UriFilter {
        override fun addToUri(builder: HttpUrl.Builder) {
            if (state != null) {
                val query = sortables[state!!.index].value
                val value = if (state!!.ascending) "asc" else "desc"
                builder.addQueryParameter("order[$query]", value)
            }
        }
    }

    enum class ApiTerms(val term: String) {
        AUTHORS("author"),
        ARTISTS("artist"),
        THEMES("theme"),
        GENRES("genre"),
    }
}
