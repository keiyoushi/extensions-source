package eu.kanade.tachiyomi.extension.id.softkomik

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LibDataDto(
    val data: List<MangaDto>,
    val maxPage: Int,
    val page: Int,
)

@Serializable
data class MangaDto(
    val gambar: String,
    val title: String,
    @SerialName("title_slug") val titleSlug: String,
    val status: String? = null,
    val type: String? = null,
)

@Serializable
data class MangaDetailsDto(
    val gambar: String,
    val title: String,
    val author: String? = null,
    @SerialName("Genre") val genre: List<String>? = emptyList(),
    val sinopsis: String? = null,
    val status: String? = null,
    val type: String? = null,
)

@Serializable
data class ChapterDto(
    val chapter: String,
)

@Serializable
data class ChapterPageImagesDto(
    val imageSrc: List<String>,
)

@Serializable
data class ChapterPageDataDto(
    @SerialName("_id") val id: String,
    val imageSrc: List<String> = emptyList(),
    val storageInter2: Boolean? = false,
    val backBS3: Boolean? = false,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class ChapterListDto(
    val chapter: List<ChapterDto>,
)

@Serializable
data class SessionDto(
    val ex: Long,
    val sign: String,
    val token: String,
    val contentAccess: ContentAccessDto? = null,
)

@Serializable
data class ContentAccessDto(
    val token: String,
    val sign: String,
)

@Serializable
class BearerTokenDto(
    val token: String,
    val ex: Long,
)
