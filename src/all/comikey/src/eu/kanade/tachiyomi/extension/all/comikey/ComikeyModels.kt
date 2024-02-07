package eu.kanade.tachiyomi.extension.all.comikey

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ComikeyComic(
    val id: Int,
    val link: String,
    val name: String,
    val author: List<ComikeyAuthor>,
    val artist: List<ComikeyAuthor>,
    val tags: List<ComikeyNameWrapper>,
    val description: String,
    val excerpt: String,
    @SerialName("full_cover") val fullCover: String,
    val e4pid: String,
    val format: Int,
    val uslug: String,
)

@Serializable
data class ComikeyChapterListResponse(
    val episodes: List<ComikeyEpisode> = emptyList(),
)

@Serializable
data class ComikeyEpisode(
    val id: String,
    val number: Float = 0F,
    val title: String,
    val subtitle: String? = null,
    val releasedAt: String,
    val availability: ComikeyEpisodeAvailability,
)

@Serializable
data class ComikeyEpisodeAvailability(
    val purchaseEnabled: Boolean = false,
)

@Serializable
data class ComikeyLmaoInitData(
    val manifest: String,
)

@Serializable
data class ComikeyNameWrapper(
    val name: String,
)

@Serializable
data class ComikeyAuthor(
    val id: Int,
    val name: String,
)
