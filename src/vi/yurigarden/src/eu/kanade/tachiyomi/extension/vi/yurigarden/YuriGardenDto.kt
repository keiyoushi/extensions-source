package eu.kanade.tachiyomi.extension.vi.yurigarden

import kotlinx.serialization.Serializable

@Serializable
class ComicsResponse(
    val comics: List<Comic> = emptyList(),
    val totalPages: Int = 0,
)

@Serializable
class Comic(
    val id: Int,
    val title: String,
    val thumbnail: String? = null,
)

@Serializable
class ComicDetail(
    val id: Int,
    val title: String,
    val description: String? = null,
    val status: String? = null,
    val thumbnail: String? = null,
    val authors: List<Author> = emptyList(),
    val genres: List<String> = emptyList(),
)

@Serializable
class Author(
    val name: String,
)

@Serializable
class ChapterData(
    val id: Int,
    val order: Double,
    val name: String? = null,
    val volume: Int? = null,
    val publishedAt: Long = 0L,
)

@Serializable
class ChapterDetail(
    val pages: List<PageData> = emptyList(),
)

@Serializable
class PageData(
    val url: String,
    val key: String? = null,
)

@Serializable
class EncryptedResponse(
    val encrypted: Boolean = false,
    val data: String? = null,
)
