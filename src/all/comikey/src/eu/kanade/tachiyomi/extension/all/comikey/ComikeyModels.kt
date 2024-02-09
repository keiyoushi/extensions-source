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
    val e4pid: String,
    val format: Int,
    val uslug: String,
    @SerialName("full_cover") val fullCover: String,
    @SerialName("update_status") val updateStatus: Int,
    @SerialName("update_text") val updateText: String,
)

@Serializable
data class ComikeyEpisodeListResponse(
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
    val finalPrice: Int = 0,
    val owned: Boolean = false,
) {
    val readable
        get() = finalPrice == 0 || owned
}

@Serializable
data class ComikeyEpisodeManifest(
    val metadata: ComikeyEpisodeManifestMetadata,
    val readingOrder: List<ComikeyPage>,
)

@Serializable
data class ComikeyEpisodeManifestMetadata(
    val readingProgression: String,
)

@Serializable
data class ComikeyPage(
    val href: String,
    val type: String,
    val height: Int,
    val width: Int,
    val alternate: List<ComikeyAlternatePage>,
)

@Serializable
data class ComikeyAlternatePage(
    val href: String,
    val type: String,
    val height: Int,
    val width: Int,
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
