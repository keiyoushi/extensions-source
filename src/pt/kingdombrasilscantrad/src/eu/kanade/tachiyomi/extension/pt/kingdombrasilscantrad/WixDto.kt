package eu.kanade.tachiyomi.extension.pt.kingdombrasilscantrad

import kotlinx.serialization.Serializable

@Serializable
data class PostFeedPageResponse(
    val postFeedPage: PostFeedPageDto,
)

@Serializable
data class PostPageResponse(
    val postPage: PostPageWrapperDto,
)

@Serializable
data class PostFeedPageDto(
    val posts: PostWrapperDto,
)

@Serializable
data class PostPageWrapperDto(
    val post: PostDto,
)

@Serializable
data class PostWrapperDto(
    val posts: List<PostDto>,
    val pagingMetaData: PagingMetadataDto,
)

@Serializable
data class PostDto(
    val id: String,
    val title: String,
    val firstPublishedDate: String,
    val url: UrlDto,
    val content: ContentDto? = null,
)

@Serializable
data class PagingMetadataDto(
    val count: Int,
    val offset: Int,
    val total: Int,
)

@Serializable
data class UrlDto(
    val base: String,
    val path: String,
)

@Serializable
data class ContentDto(
    val entityMap: Map<String, EntityMapDto>,
)

@Serializable
data class EntityMapDto(
    val type: String,
    val data: EntityMapDataDto,
)

@Serializable
data class EntityMapDataDto(
    val items: List<EntityMapItemDto>,
)

@Serializable
data class EntityMapItemDto(
    val url: String,
)
