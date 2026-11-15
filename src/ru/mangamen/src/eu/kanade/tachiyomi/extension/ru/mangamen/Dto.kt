package eu.kanade.tachiyomi.extension.ru.mangamen

import keiyoushi.utils.toJsonElement
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
class Pages(
    val p: Int,
    val u: String,
)

@Serializable
class SearchData(
    val type: List<Pair<String, String>>? = emptyList(),
    val status: List<Pair<String, String>>? = emptyList(),
    val mangaStatus: List<Pair<String, String>>? = emptyList(),
    val genres: List<Pair<String, String>>? = emptyList(),
    val tags: List<Pair<String, String>>? = emptyList(),
)

@Serializable
class FiltersWrapperDto(
    private val filters: FiltersData,
) {
    internal fun toJson(): JsonElement = SearchData(
        type = filters.types.values.map { it.name to it.id.toString() },
        status = filters.status.values.map { it.name to it.id.toString() },
        mangaStatus = filters.mangaStatus.values.map { it.name to it.id.toString() },
        genres = filters.genres.map { it.name to it.id.toString() },
        tags = filters.tags.map { it.name to it.id.toString() },
    ).toJsonElement()
}

@Serializable
class FiltersData(
    @SerialName("TYPES") val types: Map<String, FilterElement>,
    @SerialName("STATUS") val status: Map<String, FilterElement>,
    @SerialName("MANGA_STATUS") val mangaStatus: Map<String, FilterElement>,
    @SerialName("GENRES") val genres: List<FilterElement>,
    @SerialName("TAGS") val tags: List<FilterElement>,
)

@Serializable
class FilterElement(
    val id: Int,
    val name: String,
)
