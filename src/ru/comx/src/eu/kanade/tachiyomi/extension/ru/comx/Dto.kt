package eu.kanade.tachiyomi.extension.ru.comx

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class Chapters(
    @SerialName("news_id") val comicId: Int,
    val chapters: List<Chapter> = emptyList(),
)

@Serializable
class Chapter(
    val id: Int,
    val title: String,
    val number: Float,
    val date: String,
)

@Serializable
class RelatedComic(
    val name: String,
    val url: String,
    val thumbnail: String?,
)

@Serializable
class Pages(
    val host: String,
    @SerialName("host_ru") val hostRu: String,
    val images: List<String>,
)

@Serializable
class FiltersJSON(
    @SerialName("filter_items") val filterItems: FilterContent,
)

@Serializable
class FilterContent(
    @SerialName("p.cat") val pCat: FilterCategory,
    val g: FilterCategory,
    val t: FilterCategory,
    val st: FilterCategory,
)

@Serializable
class FilterCategory(
    val values: List<FilterData>,
)

@Serializable
class FilterData(
    val id: Int,
    val value: String,
)

@Serializable
class FiltersDto(
    val pcat: List<Pair<String, String>>,
    val g: List<Pair<String, String>>,
    val t: List<Pair<String, String>>,
    val st: List<Pair<String, String>>,
)
