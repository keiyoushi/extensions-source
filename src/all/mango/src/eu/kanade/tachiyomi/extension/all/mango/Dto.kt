package eu.kanade.tachiyomi.extension.all.mango

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class LibraryDto(
    val titles: List<TitleDto>? = null,
)

@Serializable
class TitleDto(
    val id: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("cover_url") val coverUrl: String,
    val entries: List<EntryDto>? = null,
    val titles: List<TitleDto>? = null,
)

@Serializable
class EntryDto(
    @SerialName("display_name") val displayName: String,
    @SerialName("title_id") val titleId: String,
    val id: String,
    val pages: Int,
    val mtime: Long,
)
