package eu.kanade.tachiyomi.extension.tr.mangadenizi

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MangaPaginated(
    val data: List<MangaItem> = emptyList(),
    @SerialName("next_page_url") val nextPageUrl: String? = null,
)

@Serializable
data class MangaItem(
    val id: Int,
    val title: String,
    val slug: String,
    val description: String? = null,
    @SerialName("cover_url") val coverUrl: String? = null,
    val status: String? = null,
    val authors: List<AuthorItem> = emptyList(),
    val categories: List<CategoryItem> = emptyList(),
    val chapters: List<ChapterItem> = emptyList(),
)

@Serializable
data class AuthorItem(val name: String)

@Serializable
data class CategoryItem(val name: String)

@Serializable
data class ChapterItem(
    val id: Int,
    val number: Double,
    val title: String? = null,
    val slug: String,
    @SerialName("published_at") val publishedAt: String,
)

@Serializable
data class PageItem(
    @SerialName("page_number") val pageNumber: Int,
    @SerialName("image_url") val imageUrl: String,
)
