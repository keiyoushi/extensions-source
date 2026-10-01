package eu.kanade.tachiyomi.extension.all.mangatoon

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class EpisodeDto(
    val id: Long,
    val title: String,
    val weight: Float,
    @SerialName("open_at") val openAt: String? = null,
    @SerialName("is_fee") val isFee: Boolean = false,
)
