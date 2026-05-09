package eu.kanade.tachiyomi.extension.en.comix

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.HttpUrl
import java.util.Calendar

class Filters {
    interface UriFilter {
        fun addToUri(builder: HttpUrl.Builder)
    }

    companion object {
        private val currentYear by lazy {
            Calendar.getInstance()[Calendar.YEAR]
        }

        // The site allows entries published anywhere from 1928 up to the year
        // after the current one (it gets used for upcoming releases).
        private const val OLDEST_YEAR = 1928

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

        // Site's curated list of 30 genres (matches the Genres section in the
        // browse panel exactly). Narrative tags like "Aliens" or "School Life"
        // used to live here too, but they belong under Tags and are searched
        // by name rather than enumerated.
        // Order mirrors the site's "Content preferences" modal (popularity-
        // ranked, not alphabetical). Used by both the GenreFilter in the
        // search sheet and the Blocked Genres source-level preference, so
        // both surfaces are consistent with the website.
        fun getGenres() = arrayOf(
            "Romance" to "23",
            "Drama" to "11",
            "Comedy" to "9",
            "Fantasy" to "12",
            "Slice of Life" to "25",
            "Action" to "6",
            "Boys Love" to "8",
            "Adventure" to "7",
            "Adult" to "87264",
            "Smut" to "87268",
            "Psychological" to "22",
            "Mystery" to "20",
            "Historical" to "14",
            "Mature" to "87267",
            "Tragedy" to "29",
            "Sci-Fi" to "24",
            "Ecchi" to "87265",
            "Horror" to "15",
            "Girls Love" to "13",
            "Isekai" to "16",
            "Hentai" to "87266",
            "Thriller" to "28",
            "Sports" to "26",
            "Crime" to "10",
            "Philosophical" to "21",
            "Mecha" to "18",
            "Wuxia" to "30",
            "Medical" to "19",
            "Superhero" to "27",
            "Magical Girls" to "17",
        )

        // The 9 site formats (matches the Formats section in the browse panel).
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

        // Order mirrors the site's "Content preferences" modal:
        // Shounen, Shoujo, Seinen, Josei. IDs are API-tied and unchanged.
        fun getDemographics() = arrayOf(
            Pair("Shounen", "2"),
            Pair("Shoujo", "1"),
            Pair("Seinen", "4"),
            Pair("Josei", "3"),
        )

        // Same set TypeFilter exposes in the search filter sheet — duplicated
        // here as a public getter so source-level preferences (in Comix.kt)
        // can present the identical pick list.
        fun getTypes() = arrayOf(
            Pair("Manga", "manga"),
            Pair("Manhwa", "manhwa"),
            Pair("Manhua", "manhua"),
            Pair("Other", "other"),
        )
    }

    fun getFilterList() = FilterList(
        // Order mirrors the site's filter panel.
        SortFilter(getSortables()),
        ContentRatingFilter(),
        TypeFilter(),
        Filter.Separator(),
        Filter.Header("Tags — comma separated"),
        TagsFilter(),
        Filter.Header("Match: AND requires every selection, OR matches any"),
        MatchModeFilter(),
        GenreFilter(getGenres()),
        FormatFilter(getFormats()),
        Filter.Separator(),
        DemographicFilter(getDemographics()),
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
            builder.addQueryParameter(param, vals[state].second)
        }
    }

    private open class UriMultiSelectOption(name: String, val value: String) : Filter.CheckBox(name)

    private open class UriMultiSelectFilter(
        name: String,
        private val param: String,
        vals: Array<Pair<String, String>>,
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
        vals: Array<Pair<String, String>>,
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

    // The site's API silently ignores `-id` / `demographics_ex[]` for this
    // field, so a TriState exclusion would never actually exclude anything.
    // Match the website which only offers include / off.
    private class DemographicFilter(demographics: Array<Pair<String, String>>) :
        UriMultiSelectFilter(
            "Demographic",
            "demographics[]",
            demographics,
        )

    private class TypeFilter :
        UriMultiSelectFilter(
            "Type",
            "types[]",
            getTypes(),
        )

    // Genres, Formats, and Tags all share the same `genres_in[]` /
    // `genres_ex[]` API parameters; the site only splits them apart for the UI.
    private abstract class TermGroupFilter(
        title: String,
        options: Array<Pair<String, String>>,
    ) : Filter.Group<UriTriSelectOption>(
        title,
        options.map { UriTriSelectOption(it.first, it.second) },
    ),
        UriFilter {
        override fun addToUri(builder: HttpUrl.Builder) {
            state.filter { it.state == TriState.STATE_INCLUDE }
                .forEach { builder.addQueryParameter("genres_in[]", it.value) }
            state.filter { it.state == TriState.STATE_EXCLUDE }
                .forEach { builder.addQueryParameter("genres_ex[]", it.value) }
        }
    }

    private class GenreFilter(genres: Array<Pair<String, String>>) : TermGroupFilter("Genres", genres)

    private class FormatFilter(formats: Array<Pair<String, String>>) : TermGroupFilter("Formats", formats)

    // Tags accept arbitrary names — the catalogue has hundreds, so we don't
    // try to enumerate them. Comix.searchMangaRequest resolves each name to an
    // ID through /api/v1/tags/search and adds the corresponding `genres_in[]`.
    class TagsFilter : Filter.Text("Tags")

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
            // The "Any" option intentionally keeps the parameter unset so the
            // site doesn't cap the results at a particular year.
            if (state != 0) super.addToUri(builder)
        }
    }

    // Author/Artist accept names; the site filters by ID, so the request
    // builder doesn't add anything here. Comix.searchMangaRequest resolves
    // the name to one or more IDs via /api/v1/tags/search and appends the
    // `authors[]` / `artists[]` parameters there.
    class AuthorFilter : Filter.Text("Author")

    class ArtistFilter : Filter.Text("Artist")

    // Controls how Tags/Genres/Formats selections combine. The site sends a
    // single `genres_mode` parameter that applies to the whole bucket; we
    // mirror that, defaulting to AND like the site does.
    private class MatchModeFilter :
        UriPartFilter(
            "Match",
            "genres_mode",
            arrayOf(
                "All (AND)" to "and",
                "Any (OR)" to "or",
            ),
        )

    // The site filters by an inclusive cap: passing "suggestive" returns safe
    // and suggestive titles, "erotica" adds those too, and so on.
    private class ContentRatingFilter :
        UriPartFilter(
            "Content rating",
            "content_rating",
            arrayOf(
                "Use preference" to "",
                "Safe only" to "safe",
                "Up to Suggestive" to "suggestive",
                "Up to Erotica" to "erotica",
                "Up to Pornographic" to "pornographic",
            ),
        ) {
        override fun addToUri(builder: HttpUrl.Builder) {
            // Index 0 is "Use preference" — let the source-level setting drive
            // the parameter instead of the manual filter.
            if (state != 0) super.addToUri(builder)
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
        Sortable("Best Match", "relevance"),
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
