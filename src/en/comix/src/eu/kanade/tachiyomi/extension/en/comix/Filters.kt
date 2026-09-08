package eu.kanade.tachiyomi.extension.en.comix

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.HttpUrl
import java.util.Calendar

class Filters(
    private val contentRating: String,
    private val selectedTypes: Set<String>,
    private val selectedDemographics: Set<String>,
    private val blockedGenres: Set<String>,
) {
    interface UriFilter {
        fun addToUri(builder: HttpUrl.Builder)
    }

    interface PreferenceFilter : UriFilter

    interface QueryAwareFilter : UriFilter {
        fun addToUri(builder: HttpUrl.Builder, query: String)
    }

    interface RequiresTermSelection : UriFilter

    interface TermFilter {
        val hasSelection: Boolean
    }

    companion object {
        private val currentYear by lazy {
            Calendar.getInstance()[Calendar.YEAR]
        }

        private const val OLDEST_YEAR = 1928

        private val CONTENT_RATING_OPTIONS = arrayOf(
            "Show all" to "",
            "Safe only" to "safe",
            "Up to Suggestive" to "suggestive",
            "Up to Erotica" to "erotica",
            "Up to Pornographic" to "pornographic",
        )

        private fun getYearsArray(forFromFilter: Boolean): Array<Pair<String, String>> {
            val newest = currentYear + 1
            val years = (newest downTo OLDEST_YEAR).map { it.toString() to it.toString() }
            val any = "Any" to ""
            return if (forFromFilter) {
                (years + any).toTypedArray()
            } else {
                (listOf(any) + years).toTypedArray()
            }
        }

        fun getGenres() = arrayOf(
            "Action" to "6",
            "Adult" to "87264",
            "Adventure" to "7",
            "Boys Love" to "8",
            "Comedy" to "9",
            "Crime" to "10",
            "Drama" to "11",
            "Ecchi" to "87265",
            "Fantasy" to "12",
            "Girls Love" to "13",
            "Harem" to "40",
            "Hentai" to "87266",
            "Historical" to "14",
            "Horror" to "15",
            "Isekai" to "16",
            "Magical Girls" to "17",
            "Mature" to "87267",
            "Mecha" to "18",
            "Medical" to "19",
            "Mystery" to "20",
            "Philosophical" to "21",
            "Psychological" to "22",
            "Romance" to "23",
            "Sci-Fi" to "24",
            "Slice of Life" to "25",
            "Smut" to "87268",
            "Sports" to "26",
            "Superhero" to "27",
            "Thriller" to "28",
            "Tragedy" to "29",
            "Wuxia" to "30",
        )

        fun getFormats() = arrayOf(
            "4-Koma" to "93164",
            "Adaptation" to "93167",
            "Anthology" to "93165",
            "Award Winning" to "93166",
            "Doujinshi" to "93168",
            "Full Color" to "93172",
            "Long Strip" to "93170",
            "Oneshot" to "93169",
            "Web Comic" to "93171",
        )

        fun getDemographics() = arrayOf(
            Pair("Josei", "3"),
            Pair("Seinen", "4"),
            Pair("Shoujo", "1"),
            Pair("Shounen", "2"),
        )

        fun getTypes() = arrayOf(
            Pair("Manga", "manga"),
            Pair("Manhwa", "manhwa"),
            Pair("Manhua", "manhua"),
            Pair("Other", "other"),
        )

        fun getContentRatingsUpTo(maxRating: String): List<String> {
            if (maxRating.isEmpty()) return emptyList()
            val ratings = listOf("safe", "suggestive", "erotica", "pornographic")
            val index = ratings.indexOf(maxRating)
            return if (index == -1) emptyList() else ratings.take(index + 1)
        }
    }

    fun getFilterList() = FilterList(
        SortFilter(getSortables()),
        ContentRatingFilter(contentRating),
        TypeFilter(selectedTypes),
        Filter.Separator(),
        Filter.Header("Tags — comma separated"),
        TagsFilter(),
        Filter.Header("Match: AND requires every selection, OR matches any"),
        MatchModeFilter(),
        GenreFilter(getGenres(), blockedGenres),
        FormatFilter(getFormats()),
        Filter.Separator(),
        DemographicFilter(getDemographics(), selectedDemographics),
        StatusFilter(),
        MinChapterFilter(),
        Filter.Separator(),
        Filter.Header("Release Year"),
        YearFromFilter(),
        YearToFilter(),
        Filter.Separator(),
        Filter.Header("Author / Artist — comma separated"),
        AuthorFilter(),
        ArtistFilter(),
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
            vals[state].second.takeIf { it.isNotEmpty() }?.let {
                builder.addQueryParameter(param, it)
            }
        }
    }

    private open class UriMultiSelectOption(name: String, val value: String) : Filter.CheckBox(name)

    private open class UriMultiSelectFilter(
        name: String,
        private val param: String,
        private val vals: Array<Pair<String, String>>,
        private val selectedValues: Set<String> = emptySet(),
        private val onlyIfSome: Boolean = false,
    ) : Filter.Group<UriMultiSelectOption>(
        name,
        vals.map { (name, value) ->
            UriMultiSelectOption(name, value).apply { state = value in selectedValues }
        },
    ),
        UriFilter {
        override fun addToUri(builder: HttpUrl.Builder) {
            val checked = state.filter { it.state }
            // Ignore when no explicit inclusion (default)
            if (onlyIfSome && checked.size == vals.size) return

            checked.forEach {
                builder.addQueryParameter(param, it.value)
            }
        }
    }

    private open class UriTriSelectOption(name: String, val value: String) : Filter.TriState(name)

    // The API ignores demographic exclusions, so this must remain include/off.
    private class DemographicFilter(
        demographics: Array<Pair<String, String>>,
        selectedValues: Set<String>,
    ) : UriMultiSelectFilter(
        "Demographic",
        "demographics[]",
        demographics,
        selectedValues,
        true,
    ),
        PreferenceFilter

    private class TypeFilter(selectedValues: Set<String>) :
        UriMultiSelectFilter(
            "Type",
            "types[]",
            getTypes(),
            selectedValues,
            true,
        ),
        PreferenceFilter

    private abstract class TermGroupFilter(
        title: String,
        options: Array<Pair<String, String>>,
        excludedValues: Set<String> = emptySet(),
    ) : Filter.Group<UriTriSelectOption>(
        title,
        options.map { (name, value) ->
            UriTriSelectOption(name, value).apply {
                if (value in excludedValues) state = TriState.STATE_EXCLUDE
            }
        },
    ),
        UriFilter,
        TermFilter {
        override val hasSelection
            get() = state.any { it.state != TriState.STATE_IGNORE }

        override fun addToUri(builder: HttpUrl.Builder) {
            state.filter { it.state == TriState.STATE_INCLUDE }
                .forEach { builder.addQueryParameter("genres_in[]", it.value) }
            state.filter { it.state == TriState.STATE_EXCLUDE }
                .forEach { builder.addQueryParameter("genres_ex[]", it.value) }
        }
    }

    private class GenreFilter(
        genres: Array<Pair<String, String>>,
        blockedGenres: Set<String>,
    ) : TermGroupFilter("Genres", genres, blockedGenres),
        PreferenceFilter

    private class FormatFilter(formats: Array<Pair<String, String>>) : TermGroupFilter("Formats", formats)

    class TagsFilter : Filter.Text("Tags")

    private class StatusFilter :
        UriMultiSelectFilter(
            "Status",
            "statuses[]",
            arrayOf(
                Pair("Releasing", "releasing"),
                Pair("Finished", "finished"),
                Pair("On Hiatus", "on_hiatus"),
                Pair("Discontinued", "discontinued"),
                Pair("Not Yet Released", "not_yet_released"),
            ),
        )

    private class YearFromFilter :
        UriPartFilter(
            "From",
            "year_from",
            getYearsArray(forFromFilter = true),
            "",
        )

    private class YearToFilter :
        UriPartFilter(
            "To",
            "year_to",
            getYearsArray(forFromFilter = false),
            "",
        ) {
        override fun addToUri(builder: HttpUrl.Builder) {
            if (state > 0) super.addToUri(builder)
        }
    }

    class AuthorFilter : Filter.Text("Author")

    class ArtistFilter : Filter.Text("Artist")

    private class MatchModeFilter :
        UriPartFilter(
            "Match",
            "genres_mode",
            arrayOf(
                "All (AND)" to "and",
                "Any (OR)" to "or",
            ),
        ),
        RequiresTermSelection

    private class ContentRatingFilter(defaultValue: String) :
        UriPartFilter(
            "Content rating",
            "content_rating",
            CONTENT_RATING_OPTIONS,
            defaultValue,
        ),
        PreferenceFilter {
        override fun addToUri(builder: HttpUrl.Builder) {
            Filters.getContentRatingsUpTo(CONTENT_RATING_OPTIONS[state].second)
                .takeIf { it.isNotEmpty() }
                ?.let { ratings ->
                    ratings.map {
                        builder.addQueryParameter("content_rating[]", it)
                    }
                }
        }
    }

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

    private class Sortable(val title: String, val value: String) {
        override fun toString(): String = title
    }

    private fun getSortables() = arrayOf(
        Sortable("Relevance", "relevance"),
        Sortable("Latest update", "chapter_updated_at"),
        Sortable("Recently added", "created_at"),
        Sortable("Title", "title"),
        Sortable("Year", "year"),
        Sortable("Highest rated", "score"),
        Sortable("Most viewed · 7 days", "views_7d"),
        Sortable("Most viewed · 30 days", "views_30d"),
        Sortable("Most viewed · 90 days", "views_90d"),
        Sortable("Most viewed · all time", "views_total"),
        Sortable("Most followed", "follows_total"),
    )

    private class SortFilter(private val sortables: Array<Sortable>) :
        Filter.Sort(
            "Sort By",
            sortables.map(Sortable::title).toTypedArray(),
            Selection(0, false),
        ),
        QueryAwareFilter {
        override fun addToUri(builder: HttpUrl.Builder) {
            addToUri(builder, "")
        }

        override fun addToUri(builder: HttpUrl.Builder, query: String) {
            if (state != null) {
                val selected = sortables[state!!.index].value
                val order = if (selected == "relevance" && query.isBlank()) {
                    "chapter_updated_at"
                } else {
                    selected
                }
                val value = if (state!!.ascending) "asc" else "desc"

                builder.addQueryParameter("order[$order]", value)
            }
        }
    }
}
