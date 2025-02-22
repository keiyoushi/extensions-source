package eu.kanade.tachiyomi.multisrc.gravureblogger
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class BloggerDto(
    val feed: BloggerFeedDto,
)

@Serializable
class BloggerFeedDto(
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
class BloggerTextDto(
    @SerialName("\$t") val t: String,
)
