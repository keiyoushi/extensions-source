package eu.kanade.tachiyomi.extension.es.samatodencomics

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BloggerFeedDto(
    @SerialName("feed") val feed: BloggerFeedDataDto? = null,
    @SerialName("entry") val entry: BloggerEntryDto? = null,
)

@Serializable
data class BloggerFeedDataDto(
    @SerialName("entry") val entries: List<BloggerEntryDto>? = null,
    @SerialName("openSearch\$totalResults") val totalResults: BloggerTextDto? = null,
    @SerialName("openSearch\$startIndex") val startIndex: BloggerTextDto? = null,
    @SerialName("openSearch\$itemsPerPage") val itemsPerPage: BloggerTextDto? = null,
)

@Serializable
data class BloggerEntryDto(
    @SerialName("title") val title: BloggerTextDto? = null,
    @SerialName("content") val content: BloggerTextDto? = null,
    @SerialName("published") val published: BloggerTextDto? = null,
    @SerialName("link") val links: List<BloggerLinkDto>? = null,
    @SerialName("category") val categories: List<BloggerCategoryDto>? = null,
    @SerialName("media\$thumbnail") val mediaThumbnail: BloggerMediaThumbnailDto? = null,
)

@Serializable
data class BloggerTextDto(
    @SerialName("\$t") val text: String? = null,
)

@Serializable
data class BloggerLinkDto(
    @SerialName("rel") val rel: String? = null,
    @SerialName("href") val href: String? = null,
)

@Serializable
data class BloggerCategoryDto(
    @SerialName("term") val term: String? = null,
)

@Serializable
data class BloggerMediaThumbnailDto(
    @SerialName("url") val url: String? = null,
)
