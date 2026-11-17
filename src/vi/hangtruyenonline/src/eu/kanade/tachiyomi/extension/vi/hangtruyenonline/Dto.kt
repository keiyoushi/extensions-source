package eu.kanade.tachiyomi.extension.vi.hangtruyenonline

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class GenreOption(
    val name: String,
    val id: String,
)

@Serializable
class CategoryOption(
    val name: String,
    val id: String,
)

@Serializable
class FilterData(
    val genres: List<GenreOption>,
    val categories: List<CategoryOption>,
)

@Serializable
class MangaDetailDto(
    val title: String,
    val slug: String,
    val overview: String? = null,
    val posterPath: String? = null,
    val author: String? = null,
    val genres: List<MangaGenre> = emptyList(),
)

@Serializable
class MangaGenre(
    val name: String,
)

@Serializable
class ChapterDetailDto(
    val pages: List<ChapterPage> = emptyList(),
)

@Serializable
class ChapterPage(
    @SerialName("page") val index: Int,
    val url: String,
)
