package eu.kanade.tachiyomi.extension.en.batcave

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class XFilters(
    @SerialName("filter_items") val filterItems: XFilterItems = XFilterItems(),
)

@Serializable
class XFilterItems(
    @SerialName("p") val publisher: XFilterItem = XFilterItem(),
    @SerialName("g") var genre: XFilterItem = XFilterItem(),

)

@Serializable
class XFilterItem(
    val values: ArrayList<Values> = arrayListOf(),
)

@Serializable
class Values(
    val id: Int,
    val value: String,
)

@Serializable
class Chapters(
    @SerialName("news_id") val comicId: Int,
    val chapters: List<Chapter>,
    val xhash: String,
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
    val images: List<String>,
)
