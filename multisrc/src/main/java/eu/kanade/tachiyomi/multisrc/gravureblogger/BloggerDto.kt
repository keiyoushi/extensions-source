package eu.kanade.tachiyomi.multisrc.gravureblogger

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BloggerDto(
    val feed: BloggerFeedDto,
)

@Serializable
data class BloggerFeedDto(
    @SerialName("openSearch\$totalResults") val totalResults: BloggerTextDto,
    @SerialName("openSearch\$startIndex") val startIndex: BloggerTextDto,
    @SerialName("openSearch\$itemsPerPage") val itemsPerPage: BloggerTextDto,
    val category: List<BloggerCategoryDto> = emptyList(),
    val entry: List<BloggerFeedEntryDto> = emptyList(),
)

@Serializable
data class BloggerFeedEntryDto(
    val published: BloggerTextDto,
    val category: List<BloggerCategoryDto>,
    val title: BloggerTextDto,
    val content: BloggerTextDto,
    val link: List<BloggerLinkDto>,
    val author: List<BloggerAuthorDto>,
)

@Serializable
data class BloggerLinkDto(
    val rel: String,
    val href: String,
)

@Serializable
data class BloggerCategoryDto(
    val term: String,
)

@Serializable
data class BloggerAuthorDto(
    val name: BloggerTextDto,
)

@Serializable
data class BloggerTextDto(
    @SerialName("\$t") val t: String,
)
