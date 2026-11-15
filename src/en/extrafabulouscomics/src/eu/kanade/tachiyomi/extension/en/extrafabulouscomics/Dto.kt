package eu.kanade.tachiyomi.extension.en.extrafabulouscomics

import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
class AccessTokensDto(
    val apps: Map<String, AppDto>,
)

@Serializable
class AppDto(
    val instance: String,
)

@Serializable
class PostsQueryDto(
    private val query: QueryDto,
)

@Serializable
class QueryDto(
    private val paging: PagingDto,
)

@Serializable
class PagingDto(
    private val offset: Int,
    private val limit: Int,
)

@Serializable
class PostsDto(
    val posts: List<PostDto>,
    val pagingMetadata: PagingMetadataDto,
)

@Serializable
class PagingMetadataDto(
    val total: Int,
)

@Serializable
class PostDto(
    private val title: String,
    private val slug: String,
    private val firstPublishedDate: String,
) {
    fun toSChapter(number: Int) = SChapter.create().apply {
        url = "/$slug"
        name = title
        chapter_number = number.toFloat()
        date_upload = Instant.tryParse(firstPublishedDate)
    }
}

@Serializable
class PostPageDto(
    private val postPage: PostPageDataDto,
) {
    fun imageIds() = postPage.post.richContent.nodes.mapNotNull { it.imageData?.image?.src?.id }
}

@Serializable
class PostPageDataDto(
    val post: PostContentDto,
)

@Serializable
class PostContentDto(
    val richContent: RichContentDto,
)

@Serializable
class RichContentDto(
    val nodes: List<NodeDto>,
)

@Serializable
class NodeDto(
    val imageData: ImageDataDto? = null,
)

@Serializable
class ImageDataDto(
    val image: ImageDto,
)

@Serializable
class ImageDto(
    val src: ImageSrcDto,
)

@Serializable
class ImageSrcDto(
    val id: String,
)
