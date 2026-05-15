package eu.kanade.tachiyomi.extension.en.batcave

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class XFilters(
    @SerialName("filter_items") private val filterItems: XFilterItems,
) {
    val publishers get() = filterItems.publisher.values
    val genres get() = filterItems.genre.values
}

@Serializable
class XFilterItems(
    @SerialName("p") val publisher: XFilterItem,
    @SerialName("g") val genre: XFilterItem,
)

@Serializable
class XFilterItem(
    val values: List<FilterValue> = emptyList(),
)

@Serializable
class FilterValue(
    val id: Int,
    val value: String,
)

@Serializable
class Chapters(
    @SerialName("news_id") val comicId: Int,
    val chapters: List<Chapter> = emptyList(),
    val xhash: String = "",
)

@Serializable
class Chapter(
    val id: Int,
    @SerialName("posi") val number: Float,
    val title: String,
    val date: String,
)

@Serializable
class Images(
    val images: List<String> = emptyList(),
)
