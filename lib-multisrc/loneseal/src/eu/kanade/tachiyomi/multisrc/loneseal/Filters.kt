package eu.kanade.tachiyomi.multisrc.loneseal

import eu.kanade.tachiyomi.source.model.Filter
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl

interface UriQueryFilter {
    fun addToQuery(builder: HttpUrl.Builder)
}

open class SelectFilter(
    displayName: String,
    private val field: String,
    private val vals: Array<Pair<String, String?>>,
    defaultState: Int = 0,
) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray(), defaultState),
    UriQueryFilter {
    fun selectedValue(): String? = vals[state].second

    override fun addToQuery(builder: HttpUrl.Builder) {
        selectedValue()?.let { builder.addQueryParameter(field, it) }
    }
}

class SortFilter(vals: Array<Pair<String, String?>>) : SelectFilter("Sort", "sort", vals, 2)

class OrderFilter(vals: Array<Pair<String, String?>>) : SelectFilter("Order", "order", vals)

class StatusFilter(vals: Array<Pair<String, String?>>) : SelectFilter("Status", "status", vals)

class GenreFilter(vals: Array<Pair<String, String?>>) : SelectFilter("Genre", "genre", vals)

class TypeFilter(vals: Array<Pair<String, String?>>) : SelectFilter("Type", "comic_type", vals)

class ColorFilter(vals: Array<Pair<String, String?>>) : SelectFilter("Color", "color_format", vals)

class ReadingFilter(vals: Array<Pair<String, String?>>) : SelectFilter("Reading", "reading_format", vals)

class TextFilter(name: String, private val queryKey: String) :
    Filter.Text(name),
    UriQueryFilter {
    override fun addToQuery(builder: HttpUrl.Builder) {
        state.takeIf { it.isNotBlank() }?.let { builder.addQueryParameter(queryKey, it) }
    }
}

class CheckBoxFilter(name: String, private val queryKey: String, private val checkedValue: String = "1") :
    Filter.CheckBox(name),
    UriQueryFilter {
    override fun addToQuery(builder: HttpUrl.Builder) {
        if (state) builder.addQueryParameter(queryKey, checkedValue)
    }
}

internal val sortOptions: Array<Pair<String, String?>> = arrayOf(
    "Latest" to "latest",
    "New" to "new",
    "Top Views" to "views",
    "Top Rate" to "rate",
    "Top Bookmark" to "bookmark",
    "Title A-Z" to "az",
    "Title Z-A" to "za",
)

internal val orderOptions: Array<Pair<String, String?>> = arrayOf(
    "Descending" to "desc",
    "Ascending" to "asc",
)

internal val statusOptions: Array<Pair<String, String?>> = arrayOf(
    "All" to null,
    "Ongoing" to "ONGOING",
    "Completed" to "COMPLETED",
    "Hiatus" to "HIATUS",
)

internal val typeOptions: Array<Pair<String, String?>> = arrayOf(
    "All" to null,
    "Manga" to "MANGA",
    "Manhwa" to "MANHWA",
    "Manhua" to "MANHUA",
)

internal val colorOptions: Array<Pair<String, String?>> = arrayOf(
    "All" to null,
    "Full Color" to "FULL_COLOR",
    "B&W" to "BW",
)

internal val readingOptions: Array<Pair<String, String?>> = arrayOf(
    "All" to null,
    "Vertical Scroll" to "VERTICAL_SCROLL",
    "Page" to "PAGE",
)

internal val seriesTagOptions: Array<Pair<String, String?>> = arrayOf(
    "All" to null,
    "ST8" to "ST8",
    "BL" to "BL",
)

internal val fallbackGenres: Array<Pair<String, String>> = arrayOf(
    "Action" to "action",
    "Adult" to "adult",
    "Adventure" to "adventure",
    "Comedy" to "comedy",
    "Drama" to "drama",
    "Ecchi" to "ecchi",
    "Fantasy" to "fantasy",
    "Gender Bender" to "gender-bender",
    "Harem" to "harem",
    "Historical" to "historical",
    "Horror" to "horror",
    "Isekai" to "isekai",
    "Josei" to "josei",
    "Martial Arts" to "martial-arts",
    "Mature" to "mature",
    "Mystery" to "mystery",
    "Psychological" to "psychological",
    "Romance" to "romance",
    "School Life" to "school-life",
    "Sci Fi" to "sci-fi",
    "Seinen" to "seinen",
    "Shoujo" to "shoujo",
    "Slice Of Life" to "slice-of-life",
    "Smut" to "smut",
    "Sports" to "sports",
    "Supernatural" to "supernatural",
    "Thriller" to "thriller",
    "Tragedy" to "tragedy",
)

internal fun genreOptions(data: JsonElement?, overloadedGenres: Set<String>) = buildList {
    add("All" to null)
    val genres = data?.parseAs<List<GenreDto>>()
        ?.takeIf { it.isNotEmpty() }
        ?.map { it.name to it.slug }
        ?: fallbackGenres.toList()
    addAll(genres.filterNot { it.second.isEmpty() || it.second in overloadedGenres })
}.toTypedArray()
