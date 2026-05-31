package eu.kanade.tachiyomi.extension.all.comikey

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class ComikeyComic(
    val link: String,
    val name: String,
    val author: List<ComikeyAuthor>,
    val artist: List<ComikeyAuthor>,
    val tags: List<ComikeyNameWrapper>,
    val description: String,
    val excerpt: String,
    val format: Int,
    @SerialName("full_cover") val fullCover: String,
    @SerialName("update_status") val updateStatus: Int,
    @SerialName("update_text") val updateText: String,
)

@Serializable
class ComikeyEpisodeListResponse(
    val episodes: List<ComikeyEpisode> = emptyList(),
)

@Serializable
class ComikeyEpisode(
    val id: String,
    val number: Float = 0F,
    val title: String,
    val subtitle: String? = null,
    val releasedAt: String,
    private val finalPrice: Int = 0,
    private val owned: Boolean = false,
) {
    val readable: Boolean
        get() = finalPrice == 0 || owned
}

@Serializable
class ComikeyEpisodeManifest(
    val metadata: ComikeyEpisodeManifestMetadata,
    val readingOrder: List<ComikeyPage>,
)

@Serializable
class ComikeyEpisodeManifestMetadata(
    val readingProgression: String,
)

@Serializable
class ComikeyPage(
    val href: String,
    val type: String,
    val height: Int,
    val width: Int,
    val alternate: List<ComikeyAlternatePage> = emptyList(),
)

@Serializable
class ComikeyAlternatePage(
    val href: String,
    val type: String,
    val height: Int,
    val width: Int,
)

@Serializable
class ComikeyNameWrapper(
    val name: String,
)

@Serializable
class ComikeyAuthor(
    val name: String,
)
