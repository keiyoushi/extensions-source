package eu.kanade.tachiyomi.extension.vi.kamicomic

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class SearchResult(
    val title: String? = null,
    val url: String? = null,
    val thumb: String? = null,
)

@Serializable
class WpManga(
    val slug: String? = null,
    val title: WpRendered? = null,
    val content: WpRendered? = null,
    @SerialName("_embedded")
    val embedded: WpEmbedded? = null,
)

@Serializable
class WpRendered(
    val rendered: String? = null,
)

@Serializable
class WpEmbedded(
    @SerialName("wp:featuredmedia")
    val featuredMedia: List<WpFeaturedMedia>? = null,
    @SerialName("wp:term")
    val terms: List<List<WpTerm>>? = null,
)

@Serializable
class WpFeaturedMedia(
    @SerialName("source_url")
    val sourceUrl: String? = null,
)

@Serializable
class WpTerm(
    val name: String? = null,
    val slug: String? = null,
    val taxonomy: String? = null,
)
