package eu.kanade.tachiyomi.extension.ar.brownmanga

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class QueryBody(
    val table: String,
    val select: String = "*",
    val filters: List<Filter>? = null,
    val order: Order? = null,
    val limit: Int? = null,
    val offset: Int? = null,
)

@Serializable
class Filter(
    val col: String,
    val op: String,
    val `val`: String,
)

@Serializable
class Order(
    val column: String,
    val ascending: Boolean,
)

@Serializable
class ManhwaDto(
    val id: String,
    val title: String,
    @SerialName("title_ar") val titleAr: String? = null,
    val slug: String,
    @SerialName("cover_url") val coverUrl: String? = null,
    val status: String? = null,
    val type: String? = null,
    val description: String? = null,
    @SerialName("description_ar") val descriptionAr: String? = null,
)

@Serializable
class ChapterDto(
    val id: String,
    @SerialName("chapter_number") val chapterNumber: Double? = null,
    val title: String? = null,
    @SerialName("is_locked") val isLocked: Boolean? = null,
    @SerialName("manhwa_id") val manhwaId: String? = null,
)

@Serializable
class ChapterPageDto(
    @SerialName("page_number") val pageNumber: Int? = null,
    @SerialName("image_url") val imageUrl: String? = null,
)

@Serializable
class ChWithManhwaDto(
    val id: String,
    @SerialName("chapter_number") val chapterNumber: Double? = null,
    val title: String? = null,
    @SerialName("manhwa_id") val manhwaId: String? = null,
    val manhwa: ManhwaDto? = null,
)
