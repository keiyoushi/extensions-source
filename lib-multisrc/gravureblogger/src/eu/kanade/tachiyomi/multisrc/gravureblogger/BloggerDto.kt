package eu.kanade.tachiyomi.multisrc.gravureblogger

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class BloggerDto(
    val feed: BloggerFeedDto,
)

@Serializable
class BloggerFeedDto(
    @SerialName("openSearch\$totalResults") val totalResults: BloggerTextDto,
    @SerialName("openSearch\$startIndex") val startIndex: BloggerTextDto,
    @SerialName("openSearch\$itemsPerPage") val itemsPerPage: BloggerTextDto,
    val category: List<BloggerCategoryDto> = emptyList(),
    val entry: List<BloggerFeedEntryDto> = emptyList(),
)

@Serializable
class BloggerFeedEntryDto(
    val published: BloggerTextDto,
    val category: List<BloggerCategoryDto>? = emptyList(),
    val title: BloggerTextDto,
    val content: BloggerTextDto,
    val link: List<BloggerLinkDto>,
    val author: List<BloggerAuthorDto>? = emptyList(),
)

@Serializable
class BloggerLinkDto(
    val rel: String,
    val href: String,
)

@Serializable
class BloggerCategoryDto(
    val term: String,
)

@Serializable
class BloggerAuthorDto(
    val name: BloggerTextDto,
)

@Serializable
class BloggerTextDto(
    @SerialName("\$t") val t: String,
)
