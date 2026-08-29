package eu.kanade.tachiyomi.extension.vi.kamicomic

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class WpManga(
    val title: WpRendered,
    val content: WpRendered? = null,
    @SerialName("_embedded")
    val embedded: WpEmbedded? = null,
)

@Serializable
class WpRendered(
    val rendered: String,
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
    val taxonomy: String? = null,
)
