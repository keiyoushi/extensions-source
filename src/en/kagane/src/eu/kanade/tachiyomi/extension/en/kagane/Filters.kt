package eu.kanade.tachiyomi.extension.en.kagane

import eu.kanade.tachiyomi.extension.en.kagane.Kagane.Companion.CONTENT_RATINGS
import eu.kanade.tachiyomi.source.model.Filter
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

@Serializable
data class MetadataDto(
    val genres: Map<String, String>,
    val tags: Map<String, String>,
    val sources: Map<String, String>,
) {
    fun getGenresList() = genres
        .map { (k, v) -> FilterData(k, v) }.sortedBy { it.name }
    fun getTagsList() = tags
        .map { (k, v) -> FilterData(k, v.replaceFirstChar { c -> c.uppercase() }) }.sortedBy { it.name }
    fun getSourcesList() = sources
        .map { (k, v) -> FilterData(k, v) }.sortedBy { it.name }
}

internal class SortFilter(
    selection: Selection = Selection(0, false),
    private val options: List<SelectFilterOption> = getSortFilter(),
) : Filter.Sort(
    "Sort By",
    options.map { it.name }.toTypedArray(),
    selection,
) {
    val selected: SelectFilterOption
        get() = state?.index?.let { options.getOrNull(it) } ?: options[0]

    fun toUriPart(): String {
        val base = selected.value
        val order = if (state?.ascending == true) "" else ",desc"
        return if (base.isNotEmpty()) base + order else ""
    }
}

private fun getSortFilter() = listOf(
    SelectFilterOption("Relevance", ""),
    SelectFilterOption("Popular (Total Views)", "total_views"),
    SelectFilterOption("Popular (Average Views)", "avg_views"),
    SelectFilterOption("Popular (Today)", "avg_views_today"),
    SelectFilterOption("Popular (Week)", "avg_views_week"),
    SelectFilterOption("Popular (Month)", "avg_views_month"),
    SelectFilterOption("Latest", "updated_at"),
    SelectFilterOption("By Name", "series_name"),
    SelectFilterOption("Books count", "books_count"),
    SelectFilterOption("Created at", "created_at"),
)

internal class SelectFilterOption(val name: String, val value: String)

internal class ContentRatingFilter(
    defaultRatings: Set<String>,
    ratings: List<FilterData> = CONTENT_RATINGS.map {
        FilterData(it.replaceFirstChar { c -> c.uppercase() }, it.replaceFirstChar { c -> c.uppercase() })
    },
) : JsonMultiSelectFilter(
    "Content Rating",
    "content_rating",
    ratings.map {
        MultiSelectOption(it.name, it.id).apply {
            state = defaultRatings.contains(it.id.lowercase())
        }
    },
)

internal class GenresFilter(
    genres: List<FilterData>,
) : JsonMultiSelectTriFilter(
    "Genres",
    "genres",
    genres.map {
        MultiSelectTriOption(it.name, it.id)
    },
)

internal class TagsSearchFilter : Filter.Text(" Tags (e.g. Medieval, -Politics)")

internal class SourcesFilter(
    sources: List<FilterData>,
) : JsonMultiSelectFilter(
    "Sources",
    "sources",
    sources.map {
        MultiSelectOption(it.name, it.id)
    },
)

class FilterData(
    val id: String,
    val name: String,
)

internal open class MultiSelectOption(name: String, val id: String = name) : Filter.CheckBox(name, false)

internal open class JsonMultiSelectFilter(
    name: String,
    private val param: String,
    genres: List<MultiSelectOption>,
) : Filter.Group<MultiSelectOption>(name, genres),
    JsonFilter {
    override fun addToJsonObject(
        builder: JsonObjectBuilder,
        key: String,
        additionExcludeList: List<String>,
    ) {
        val whatToInclude = state.filter { it.state }.map { it.id }
        with(builder) {
            if (whatToInclude.isNotEmpty()) {
                if (key.isEmpty()) {
                    putJsonArray(param) {
                        whatToInclude.forEach { add(it) }
                    }
                } else {
                    putJsonObject(key) {
                        putJsonArray(param) {
                            whatToInclude.forEach { add(it) }
                        }
                    }
                }
            }
        }
    }
}

internal open class MultiSelectTriOption(name: String, val id: String = name) : Filter.TriState(name)

internal open class JsonMultiSelectTriFilter(
    name: String,
    private val param: String,
    genres: List<MultiSelectTriOption>,
) : Filter.Group<MultiSelectTriOption>(name, genres),
    JsonFilter {
    override fun addToJsonObject(
        builder: JsonObjectBuilder,
        key: String,
        additionExcludeList: List<String>,
    ) {
        val whatToInclude = state.filter { it.state == TriState.STATE_INCLUDE }.map { it.id }
        val whatToExclude = state.filter { it.state == TriState.STATE_EXCLUDE }.map { it.id } + additionExcludeList

        with(builder) {
            if (whatToInclude.isNotEmpty() || whatToExclude.isNotEmpty()) {
                putJsonObject(key) {
                    put("match_all", true)
                    if (whatToInclude.isNotEmpty()) {
                        putJsonArray("values") {
                            whatToInclude.forEach { add(it) }
                        }
                    }

                    if (whatToExclude.isNotEmpty()) {
                        putJsonArray("exclude") {
                            whatToExclude.forEach { add(it) }
                        }
                    }
                    put("match_all", false)
                }
            }
        }
    }
}

internal interface JsonFilter {
    fun addToJsonObject(
        builder: JsonObjectBuilder,
        key: String = "",
        additionExcludeList: List<String> = emptyList(),
    )
}

internal val GenresList = arrayOf(
    "Romance",
    "Drama",
    "Manhwa",
    "Fantasy",
    "Manga",
    "Comedy",
    "Action",
    "Mature",
    "LGBTQIA+",
    "Shoujo",
    "Josei",
    "Shounen",
    "Supernatural",
    "Boys' Love",
    "Slice of Life",
    "Seinen",
    "Adventure",
    "Manhua",
    "School Life",
    "Smut",
    "Yaoi",
    "Hentai",
    "Historical",
    "Isekai",
    "Mystery",
    "Psychological",
    "Tragedy",
    "Harem",
    "Martial Arts",
    "Science Fiction",
    "Shounen Ai",
    "Ecchi",
    "Horror",
    "Girls' Love",
    "Anime",
    "Thriller",
    "Yuri",
    "Coming of Age",
    "Sports",
    "OEL",
    "Gender Bender",
    "Suspense",
    "Music",
    "Shoujo Ai",
    "Award Winning",
    "Cooking",
    "Crime",
    "Doujinshi",
    "Mecha",
    "Oneshot",
    "Philosophical",
    "Magical Girls",
    "Anthology",
    "Wuxia",
    "Medical",
    "official colored",
    "family life",
    "parody",
    "Superhero",
    "4-Koma",
    "educational",
    "self-published",
    "Animals",
    "Magic",
    "fan colored",
    "monsters",
)

val officialSources = listOf(
    "webtoon",
    "lezhin",
    "tapas",
    "comikey",
    "pocket comics",
    "day comics",
    "webcomics",
    "tappytoon",
    "toomics",
    "inkr comics",
    "manta",
    "kodoku studio",
    "dark horse comics",
    "kodansha comics",
    "seven seas entertainment",
    "square enix manga",
    "udon entertainment",
    "viz media",
    "yen press",
    "tokyopop",
    "fakku",
    "j-novel club",
    "kana",
    "vast visual",
    "one peace books",
    "booklive",
    "medibang",
    "mangadex",
    "digital manga",
    "denpa books",
    "irodori comics",
    "kodama tales",
    "shusuisha",
    "titan manga",
    "ponent mon",
    "k manga",
)
