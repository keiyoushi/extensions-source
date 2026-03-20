package eu.kanade.tachiyomi.extension.en.asurascans

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

interface UriFilter {
    fun addToUri(builder: HttpUrl.Builder)
}

class SortFilter(defaultSort: String? = null) :
    UriSortFilter(
        name = "Sort By",
        sortParam = "sort",
        orderParam = "order",
        vals = arrayOf(
            "Latest Update" to "latest",
            "Popular" to "popular",
            "Rating" to "rating",
            "A-Z" to "title",
            "Newest" to "update",
        ),
        defaultValue = defaultSort,
        ascendingValue = "asc",
        descendingValue = "desc",
    )

class StatusFilter :
    UriPartFilter(
        name = "Status",
        param = "status",
        vals = arrayOf(
            "All" to "",
            "Ongoing" to "ongoing",
            "Completed" to "completed",
            "Hiatus" to "hiatus",
            "Dropped" to "dropped",
        ),
    )

class TypeFilter :
    UriPartFilter(
        name = "Type",
        param = "type",
        vals = arrayOf(
            "All" to "",
            "Manhwa" to "manhwa",
            "Manhua" to "manhua",
            "Mangatoon" to "manga",
        ),
    )

class GenresFilter :
    UriMultiSelectFilter(
        name = "Genres",
        param = "genres",
        vals = arrayOf(
            "Action" to "action",
            "Adventure" to "adventure",
            "Comedy" to "comedy",
            "Crazy MC" to "crazy-mc",
            "Demon" to "demon",
            "Dungeons" to "dungeons",
            "Fantasy" to "fantasy",
            "Game" to "game",
            "Genius MC" to "genius-mc",
            "Isekai" to "isekai",
            "Magic" to "magic",
            "Murim" to "murim",
            "Mystery" to "mystery",
            "Necromancer" to "necromancer",
            "Overpowered" to "overpowered",
            "Regression" to "regression",
            "Reincarnation" to "reincarnation",
            "Revenge" to "revenge",
            "Romance" to "romance",
            "School Life" to "school-life",
            "Sci-fi" to "sci-fi",
            "Shoujo" to "shoujo",
            "Shounen" to "shounen",
            "System" to "system",
            "Tower" to "tower",
            "Tragedy" to "tragedy",
            "Villain" to "villain",
            "Violence" to "violence",
        ),
    )

class MinChaptersFilter :
    Filter.Text("Min Chapters"),
    UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        if (state.isNotBlank()) {
            builder.addQueryParameter("min_chapters", state)
        }
    }
}

open class UriPartFilter(
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
        val value = vals[state].second
        if (value.isNotBlank()) {
            builder.addQueryParameter(param, value)
        }
    }
}

open class UriSortFilter(
    name: String,
    private val sortParam: String,
    private val orderParam: String,
    private val vals: Array<Pair<String, String>>,
    defaultValue: String? = null,
    private val ascendingValue: String,
    private val descendingValue: String,
) : Filter.Sort(
    name,
    vals.map { it.first }.toTypedArray(),
    Selection(
        vals.indexOfFirst { it.second == defaultValue }
            .takeIf { it != -1 } ?: 0,
        ascending = false,
    ),
),
    UriFilter {

    override fun addToUri(builder: HttpUrl.Builder) {
        val state = state ?: return

        val selected = vals[state.index].second
        val order = if (state.ascending) ascendingValue else descendingValue

        builder.addQueryParameter(sortParam, selected)
        builder.addQueryParameter(orderParam, order)
    }
}

open class UriMultiSelectOption(name: String, val value: String) : Filter.CheckBox(name)

open class UriMultiSelectFilter(
    name: String,
    private val param: String,
    vals: Array<Pair<String, String>>,
) : Filter.Group<UriMultiSelectOption>(
    name,
    vals.map { UriMultiSelectOption(it.first, it.second) },
),
    UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        val checked = state.filter { it.state }.map { it.value }
        if (checked.isNotEmpty()) {
            builder.addQueryParameter(param, checked.joinToString(","))
        }
    }
}
