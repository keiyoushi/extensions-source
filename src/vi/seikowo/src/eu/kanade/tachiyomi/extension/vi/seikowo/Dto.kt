package eu.kanade.tachiyomi.extension.vi.seikowo

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class FeedResponseDto(
    val feed: FeedContainerDto,
)

@Serializable
class FeedContainerDto(
    val entry: List<FeedEntryDto>? = null,
    @SerialName("openSearch\$totalResults")
    val totalResults: ValueContainerDto? = null,
)

@Serializable
class FeedEntryDto(
    val id: ValueContainerDto? = null,
    val title: ValueContainerDto? = null,
    val content: FeedContentDto? = null,
    val updated: ValueContainerDto? = null,
    val published: ValueContainerDto? = null,
    val link: List<FeedLinkDto>? = null,
    val category: List<FeedCategoryDto>? = null,
    @SerialName("media\$thumbnail")
    val thumbnail: FeedThumbnailDto? = null,
    @SerialName("thr\$total")
    val commentsCount: ValueContainerDto? = null,
)

@Serializable
class FeedContentDto(
    @SerialName("\$t")
    val value: String? = null,
)

@Serializable
class FeedLinkDto(
    val rel: String? = null,
    val href: String? = null,
)

@Serializable
class FeedCategoryDto(
    val term: String? = null,
)

@Serializable
class FeedThumbnailDto(
    val url: String? = null,
)

@Serializable
class ValueContainerDto(
    @SerialName("\$t")
    val value: String? = null,
)

@Serializable
class FeedEntryResponseDto(
    val entry: FeedEntryDto,
)

@Serializable
class SeriesMetadataDto(
    @SerialName("series_id")
    val seriesId: String,
    val title: String,
    val author: String? = null,
    val artist: String? = null,
    val status: String? = null,
    val description: String? = null,
    val coverImage: String? = null,
    val tags: List<String>? = null,
    val chapters: List<SeriesChapterDto>? = null,
)

@Serializable
class SeriesChapterDto(
    val id: String? = null,
    val number: Double? = null,
    val chapterNum: Double? = null,
    val title: String? = null,
    val chapterTitle: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
class WorkerListRequestDto(
    val action: String,
    val labels: List<String>,
    val maxResults: Int,
    val fetchFields: String,
    val blogId: String,
)

@Serializable
class WorkerGetRequestDto(
    val action: String,
    val id: String,
    val fetchFields: String,
    val blogId: String,
)

@Serializable
class WorkerListResponseDto(
    val items: List<WorkerPostDto>? = null,
)

@Serializable
class WorkerPostDto(
    val id: String? = null,
    val content: String? = null,
)

@Serializable
class NodeChapterDto(
    val id: String? = null,
    val number: Double? = null,
    val chapterNum: Double? = null,
    val images: List<NodeImageDto>? = null,
)

@Serializable
class NodeImageDto(
    val id: String? = null,
    val dataUrl: String? = null,
)

@Serializable
class NodeChapterContainerDto(
    val chapters: List<NodeChapterDto>? = null,
)

class ChapterItem(
    val number: Double,
    val title: String?,
    val updatedAt: String?,
)

class CatalogueEntry(
    val title: String,
    val url: String,
    val thumbnailUrl: String?,
    val updatedAt: Long,
    val publishedAt: Long,
    val commentsCount: Int,
    val statusTerm: String?,
    val genres: Set<String>,
) {
    fun toSManga(): SManga = SManga.create().apply {
        url = this@CatalogueEntry.url
        title = this@CatalogueEntry.title
        thumbnail_url = this@CatalogueEntry.thumbnailUrl
    }
}
