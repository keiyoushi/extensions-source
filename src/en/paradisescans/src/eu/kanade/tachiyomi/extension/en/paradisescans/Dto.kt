package eu.kanade.tachiyomi.extension.en.paradisescans

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class ParadiseApiResponse<T>(
    val data: List<T> = emptyList(),
    val meta: MetaDto? = null,
)

@Serializable
class MetaDto(
    @SerialName("current_page") val currentPage: Int = 1,
    @SerialName("last_page") val lastPage: Int = 1,
)

@Serializable
class SeriesDto(
    val title: String,
    val slug: String,
    @SerialName("cover_url") val coverUrl: String? = null,
    val description: String? = null,
    val author: String? = null,
    val artist: String? = null,
    val status: String? = null,
    val genres: List<String> = emptyList(),
)

@Serializable
class ChapterDto(
    val id: String,
    val number: Double? = null,
    val title: String? = null,
    val price: Int? = null,
    @SerialName("is_premium") val isPremium: Boolean = false,
    @SerialName("early_access") val earlyAccess: Boolean = false,
    @SerialName("early_locked") val earlyLocked: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
class PageDto(
    @SerialName("page_number") val pageNumber: Int,
    @SerialName("image_url") val imageUrl: String,
)
