package eu.kanade.tachiyomi.multisrc.makaru

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MakaruDto(
    val feed: MakaruFeedDto,
)

@Serializable
data class MakaruFeedDto(
    @SerialName("openSearch\$totalResults") val totalResults: MakaruTextDto,
    @SerialName("openSearch\$startIndex") val startIndex: MakaruTextDto,
    @SerialName("openSearch\$itemsPerPage") val itemsPerPage: MakaruTextDto,
    val entry: List<MakaruFeedEntryDto> = emptyList(),
)

@Serializable
data class MakaruFeedEntryDto(
    val published: MakaruTextDto,
    val category: List<MakaruCategoryDto>,
    val title: MakaruTextDto,
    val content: MakaruTextDto,
    val link: List<MakaruLinkDto>,
    val author: List<MakaruAuthorDto>,
)

@Serializable
data class MakaruLinkDto(
    val rel: String,
    val href: String,
)

@Serializable
data class MakaruCategoryDto(
    val term: String,
)

@Serializable
data class MakaruAuthorDto(
    val name: MakaruTextDto,
)

@Serializable
data class MakaruTextDto(
    @SerialName("\$t") val t: String,
)
