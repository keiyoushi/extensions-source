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

        fun getGenres() = arrayOf(
            Pair("Action", "6"),
            Pair("Adult", "87264"),
            Pair("Adventure", "7"),
            Pair("Boys Love", "8"),
            Pair("Comedy", "9"),
            Pair("Crime", "10"),
            Pair("Drama", "11"),
            Pair("Ecchi", "87265"),
            Pair("Fantasy", "12"),
            Pair("Girls Love", "13"),
            Pair("Hentai", "87266"),
            Pair("Historical", "14"),
            Pair("Horror", "15"),
            Pair("Isekai", "16"),
            Pair("Magical Girls", "17"),
            Pair("Mature", "87267"),
            Pair("Mecha", "18"),
            Pair("Medical", "19"),
            Pair("Mystery", "20"),
            Pair("Philosophical", "21"),
            Pair("Psychological", "22"),
            Pair("Romance", "23"),
            Pair("Sci-Fi", "24"),
            Pair("Slice of Life", "25"),
            Pair("Smut", "87268"),
            Pair("Sports", "26"),
            Pair("Superhero", "27"),
            Pair("Thriller", "28"),
            Pair("Tragedy", "29"),
            Pair("Wuxia", "30"),
            Pair("Aliens", "31"),
            Pair("Animals", "32"),
            Pair("Cooking", "33"),
            Pair("Cross Dressing", "34"),
            Pair("Delinquents", "35"),
            Pair("Demons", "36"),
            Pair("Genderswap", "37"),
            Pair("Ghosts", "38"),
            Pair("Gyaru", "39"),
            Pair("Harem", "40"),
            Pair("Incest", "41"),
            Pair("Loli", "42"),
            Pair("Mafia", "43"),
            Pair("Magic", "44"),
            Pair("Martial Arts", "45"),
            Pair("Military", "46"),
            Pair("Monster Girls", "47"),
            Pair("Monsters", "48"),
            Pair("Music", "49"),
            Pair("Ninja", "50"),
            Pair("Office Workers", "51"),
            Pair("Police", "52"),
            Pair("Post-Apocalyptic", "53"),
            Pair("Reincarnation", "54"),
            Pair("Reverse Harem", "55"),
            Pair("Samurai", "56"),
            Pair("School Life", "57"),
            Pair("Shota", "58"),
            Pair("Supernatural", "59"),
            Pair("Survival", "60"),
            Pair("Time Travel", "61"),
            Pair("Traditional Games", "62"),
            Pair("Vampires", "63"),
            Pair("Video Games", "64"),
            Pair("Villainess", "65"),
            Pair("Virtual Reality", "66"),
            Pair("Zombies", "67"),
        )

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
        GenreFilter(getGenres()),
        TypeFilter(),
        DemographicFilter(getDemographics()),
        MinChapterFilter(),
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
                    TriState.STATE_EXCLUDE -> builder.addQueryParameter(param, "-${s.value}")
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

    private class TypeFilter :
        UriMultiSelectFilter(
            "Type",
            "types[]",
            arrayOf(
                Pair("Manga", "manga"),
                Pair("Manhwa", "manhwa"),
                Pair("Manhua", "manhua"),
                Pair("Other", "other"),
            ),
        )

    private class GenreFilter(genres: Array<Pair<String, String>>) :
        UriTriSelectFilter(
            "Genres",
            "genres[]",
            genres,
        )

    private class StatusFilter :
        UriMultiSelectFilter(
            "Status",
            "statuses[]",
            arrayOf(
                Pair("Finished", "finished"),
                Pair("Releasing", "releasing"),
                Pair("On Hiatus", "on_hiatus"),
                Pair("Discontinued", "discontinued"),
                Pair("Not Yet Released", "not_yet_released"),
            ),
        )

    private class YearFromFilter :
        UriPartFilter(
            "From",
            "release_year[from]",
            getYearsArray(includeOlder = true),
            "older",
        )

    private class YearToFilter :
        UriPartFilter(
            "To",
            "release_year[to]",
            getYearsArray(includeOlder = false),
        )

    private class MinChapterFilter :
        Filter.Text("Minimum Chapter Length"),
        UriFilter {
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
        Sortable("Total Views", "views_total"),
        Sortable("Most Follows", "follows_total"),
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
}
