package eu.kanade.tachiyomi.extension.vi.hv2tcomics

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
class ComicListResponse(
    val data: List<ComicListItem>,
    val meta: PaginationMetadata,
)

@Serializable
class PaginationMetadata(
    val total: Int,
    val page: Int,
    val limit: Int,
    @SerialName("total_pages") val totalPages: Int,
)

@Serializable
class ComicListItem(
    val id: Int,
    val title: String,
    val slug: String,
    @SerialName("cover_image") val coverImage: String,
    val status: String,
    @SerialName("new_until") val newUntil: String? = null,
    val chapters: List<ChapterDto> = emptyList(),
)

@Serializable
class ComicDetailResponse(val data: ComicDetailDto)

@Serializable
class ComicDetailDto(
    val id: Int,
    val title: String,
    val slug: String,
    @SerialName("cover_image") val coverImage: String,
    val status: String,
    val author: String? = null,
    val translator: String? = null,
    @SerialName("other_names") val otherNames: String? = null,
    val description: String? = null,
    @SerialName("view_count") val viewCount: Int = 0,
    @SerialName("follower_count") val followerCount: Int = 0,
    val tags: List<TagDto> = emptyList(),
    val chapters: List<ChapterDto> = emptyList(),
)

@Serializable
class ChapterDto(
    val id: Int,
    val title: String? = null,
    val slug: String,
    @SerialName("chapter_number") val chapterNumber: Double,
    val price: Int = 0,
    @SerialName("view_count") val viewCount: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("published_at") val publishedAt: String? = null,
)

@Serializable
class TagDto(
    val id: Int,
    val name: String,
    val slug: String,
)

@Serializable
class TagResponse(
    val data: List<TagDto>,
)

@Serializable
class TranslatorResponse(
    val data: List<String>,
)

fun ComicListItem.toSManga(): SManga = SManga.create().apply {
    url = slug
    title = this@toSManga.title
    thumbnail_url = coverImage
    status = when (this@toSManga.status) {
        "ONGOING" -> SManga.ONGOING
        "COMPLETED" -> SManga.COMPLETED
        else -> SManga.ONGOING
    }
}

fun ComicDetailDto.toSManga(): SManga = SManga.create().apply {
    url = slug
    title = this@toSManga.title
    thumbnail_url = coverImage
    author = this@toSManga.author
    genre = tags.joinToString { it.name }
    description = this@toSManga.description
    status = when (this@toSManga.status) {
        "ONGOING" -> SManga.ONGOING
        "COMPLETED" -> SManga.COMPLETED
        else -> SManga.ONGOING
    }
}

fun ChapterDto.toSChapter(mangaSlug: String): SChapter = SChapter.create().apply {
    url = "$mangaSlug/$slug"
    name = buildString {
        if (price > 0) append("🔒 ")
        if (chapterNumber == -1.0) {
            append("Oneshot")
        } else {
            append("Chương ")
            append(chapterNumber.toString().removeSuffix(".0"))
        }
        this@toSChapter.title?.takeIf { it.isNotEmpty() }?.let {
            append(" - ")
            append(it)
        }
    }
    date_upload = publishedAt?.let(Instant::parseOrNull)?.toEpochMilliseconds()
        ?: createdAt?.let(Instant::parseOrNull)?.toEpochMilliseconds()
        ?: 0L
}

@Serializable
class FilterData(
    val tags: List<TagOption> = emptyList(),
    val translators: List<TranslatorOption> = emptyList(),
)
