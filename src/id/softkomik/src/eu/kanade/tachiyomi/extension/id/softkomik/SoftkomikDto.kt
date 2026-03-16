package eu.kanade.tachiyomi.extension.id.softkomik

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
    val title_slug: String,
    val status: String? = null,
    val type: String? = null,
)

@Serializable
data class MangaDetailsDto(
    val gambar: String,
    val title: String,
    val author: String? = null,
    val Genre: List<String>? = emptyList(),
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
    val _id: String,
    val imageSrc: List<String>,
    val storageInter2: Boolean? = false,
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
)
