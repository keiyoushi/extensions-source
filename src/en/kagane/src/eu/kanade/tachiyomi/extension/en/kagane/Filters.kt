package eu.kanade.tachiyomi.extension.en.kagane

import eu.kanade.tachiyomi.extension.en.kagane.Kagane.Companion.CONTENT_RATINGS
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import keiyoushi.utils.parseAs
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

private var metadataFetchAttempts: Int = 0
private var metadataFetched = false

private var genresList: List<String> = emptyList()
private var tagsList: List<String> = emptyList()
private var sourcesList: List<String> = emptyList()

fun fetchMetadata(apiUrl: String, client: okhttp3.OkHttpClient) {
    if (metadataFetchAttempts < 3 && !metadataFetched) {
        try {
            client.newCall(GET("$apiUrl/api/v1/metadata"))
                .execute().parseAs<MetadataDto>()
                .let { metadata ->
                    genresList = metadata.getGenresList()
                    tagsList = metadata.getTagsList()
                    sourcesList = metadata.getSourcesList()
                    metadataFetched = true
                }
        } catch (_: Exception) {
        } finally {
            metadataFetchAttempts++
        }
    }
}

@Serializable
data class MetadataDto(
    val genres: List<MetadataTagDto>,
    val tags: List<MetadataTagDto>,
    val sources: List<MetadataTagDto>,
) {
    fun getGenresList() = genres.map { it.name }
    fun getTagsList() = tags.sortedByDescending { it.count }.slice(0..200).map { it.name }
    fun getSourcesList() = sources.map { it.name }
}

@Serializable
data class MetadataTagDto(
    val name: String,
    val count: Int = 0,
)

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
    SelectFilterOption("Popular", "avg_views"),
    SelectFilterOption("Latest", "updated_at"),
    SelectFilterOption("By Name", "series_name"),
    SelectFilterOption("Books count", "books_count"),
    SelectFilterOption("Created at", "created_at"),
)

internal class SelectFilterOption(val name: String, val value: String)

internal class ContentRatingFilter(
    defaultRatings: Set<String>,
    ratings: List<FilterData> = CONTENT_RATINGS.map { FilterData(it, it.replaceFirstChar { c -> c.uppercase() }) },
) : JsonMultiSelectFilter(
    "Content Rating",
    "content_rating",
    ratings.map {
        MultiSelectOption(it.name, it.id).apply {
            state = defaultRatings.contains(it.id)
        }
    },
)

internal class GenresFilter(
    genres: List<FilterData> = genresList.map { FilterData(it, it) },
) : JsonMultiSelectTriFilter(
    "Genres",
    "genres",
    genres.map {
        MultiSelectTriOption(it.name, it.id)
    },
)

internal class TagsFilter(
    tags: List<FilterData> = tagsList.map { FilterData(it, it.replaceFirstChar { c -> c.uppercase() }) },
) : JsonMultiSelectTriFilter(
    "Tags",
    "tags",
    tags.map {
        MultiSelectTriOption(it.name, it.id)
    },
)

internal class SourcesFilter(
    sources: List<FilterData> = sourcesList.map { FilterData(it, it) },
) : JsonMultiSelectFilter(
    "Sources",
    "sources",
    sources.map {
        MultiSelectOption(it.name, it.id)
    },
)

internal class ScanlationsFilter() : Filter.CheckBox("Show scanlations", true)

internal class FilterData(
    val id: String,
    val name: String,
)

internal open class MultiSelectOption(name: String, val id: String = name) : Filter.CheckBox(name, false)

internal open class JsonMultiSelectFilter(
    name: String,
    private val param: String,
    genres: List<MultiSelectOption>,
) : Filter.Group<MultiSelectOption>(name, genres), JsonFilter {
    override fun addToJsonObject(builder: JsonObjectBuilder) {
        val whatToInclude = state.filter { it.state }.map { it.id }

        if (whatToInclude.isNotEmpty()) {
            builder.putJsonArray(param) {
                whatToInclude.forEach { add(it) }
            }
        }
    }
}

internal open class MultiSelectTriOption(name: String, val id: String = name) : Filter.TriState(name)

internal open class JsonMultiSelectTriFilter(
    name: String,
    private val param: String,
    genres: List<MultiSelectTriOption>,
) : Filter.Group<MultiSelectTriOption>(name, genres), JsonFilter {
    override fun addToJsonObject(builder: JsonObjectBuilder) {
        val whatToInclude = state.filter { it.state == TriState.STATE_INCLUDE }.map { it.id }
        val whatToExclude = state.filter { it.state == TriState.STATE_EXCLUDE }.map { it.id }

        with(builder) {
            if (whatToInclude.isNotEmpty()) {
                putJsonObject("inclusive_$param") {
                    putJsonArray("values") {
                        whatToInclude.forEach { add(it) }
                    }
                    put("match_all", true)
                }
            }
            if (whatToExclude.isNotEmpty()) {
                putJsonObject("exclusive_$param") {
                    putJsonArray("values") {
                        whatToExclude.forEach { add(it) }
                    }
                    put("match_all", false)
                }
            }
        }
    }
}

internal interface JsonFilter {
    fun addToJsonObject(builder: JsonObjectBuilder)
}
