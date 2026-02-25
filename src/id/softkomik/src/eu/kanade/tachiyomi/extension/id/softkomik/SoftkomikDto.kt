package eu.kanade.tachiyomi.extension.id.softkomik

import kotlinx.serialization.Serializable

@Serializable
data class LibDataDto(
    val page: Int,
    val maxPage: Int,
    val data: List<MangaDto>,
)

@Serializable
data class MangaDto(
    val title: String,
    val status: String? = null,
    val type: String? = null,
    val gambar: String,
    val title_slug: String,
)

@Serializable
data class MangaDetailsDto(
    val title: String,
    val title_alt: String? = null,
    val sinopsis: String? = null,
    val author: String? = null,
    val status: String? = null,
    val type: String? = null,
    val gambar: String,
    val updated_at: String? = null,
    val Genre: List<String>? = emptyList(),
)

@Serializable
data class ChapterDto(val chapter: String)

@Serializable
data class ChapterPageImagesDto(val imageSrc: List<String> = emptyList())

@Serializable
data class ChapterPageDataDto(
    val _id: String,
    val chapter: String,
    val storageInter2: Boolean = false,
    val imageSrc: List<String> = emptyList(),
)

@Serializable
data class ChapterListDto(val chapter: List<ChapterDto>)
