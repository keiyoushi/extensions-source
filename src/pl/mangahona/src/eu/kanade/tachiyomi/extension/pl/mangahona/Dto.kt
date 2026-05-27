package eu.kanade.tachiyomi.extension.pl.mangahona

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class MangaDto(
    @SerialName("ID") val id: String,
    @SerialName("NAME") val name: String,
    @SerialName("DESCRIPTION") val description: String? = null,
    @SerialName("AUTHOR") val author: String? = null,
    @SerialName("cover_image") val coverImage: String? = null,
    @SerialName("STATUS") val status: String? = null,
    @SerialName("GENERE") val genere: String? = null,
    @SerialName("TAG") val tag: String? = null,
)

@Serializable
class ChapterDto(
    @SerialName("CHAPTER_NAME") val chapterName: String,
    @SerialName("CHAPTER_INDEX") val chapterIndex: String,
    @SerialName("DATE") val date: String? = null,
)

@Serializable
class ChapterDataDto(
    val data: String,
)

@Serializable
class PageDto(
    val src: String,
)

@Serializable
class CategoriesDto(
    val generes: List<CategoryDto>,
    val tags: List<CategoryDto>,
)

@Serializable
class CategoryDto(
    @SerialName("ID") val id: String,
    @SerialName("NAME") val name: String,
)
