package eu.kanade.tachiyomi.extension.vi.minotruyen

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class BooksResponse(
    val books: List<Book>,
    val countBook: Int? = null,
)

@Serializable
class SideHomeResponse(
    val topBooksView: List<TopBook>,
)

@Serializable
class BookDetailResponse(
    val book: BookDetail,
)

@Serializable
class ChaptersResponse(
    val chapters: List<Chapter>,
)

@Serializable
class Book(
    val bookId: Int,
    val title: String,
    val slug: String,
    val category: String? = null,
    val status: Int? = null,
    val covers: List<Cover> = emptyList(),
    val chapters: List<ChapterPreview> = emptyList(),
)

@Serializable
class TopBook(
    val bookId: Int,
    val title: String,
    val slug: String,
    val category: String? = null,
    val status: Int? = null,
    val covers: List<Cover> = emptyList(),
)

@Serializable
class BookDetail(
    val bookId: Int,
    val title: String,
    val slug: String,
    val status: Int? = null,
    val category: String? = null,
    val description: String? = null,
    val author: String? = null,
    val covers: List<Cover> = emptyList(),
    val tags: List<TagWrapper> = emptyList(),
)

@Serializable
class Chapter(
    val bookId: Int,
    val num: String,
    val chapterNumber: Double,
    val title: String? = null,
    val createdAt: String? = null,
)

@Serializable
class ChapterPreview(
    val num: String,
    val chapterNumber: Double,
    val createdAt: String? = null,
)

@Serializable
class Cover(
    val url: String,
)

@Serializable
class TagWrapper(
    val tag: Tag,
)

@Serializable
class Tag(
    val tagId: String,
    val name: String,
)

@Serializable
class ChapterServer(
    val cloud: String,
    val content: List<ChapterPage>,
)

@Serializable
class ChapterPage(
    val imageUrl: String,
    @SerialName("drm_data")
    val drmData: String? = null,
    val width: Int? = null,
    val height: Int? = null,
)
