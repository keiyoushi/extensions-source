package eu.kanade.tachiyomi.extension.en.dashtoon

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class SignUpDto(
    val idToken: String,
    val refreshToken: String,
    val expiresIn: String,
)

@Serializable
class RefreshDto(
    @SerialName("id_token") val idToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: String,
)

@Serializable
class ShowDto(
    val id: String,
    val name: String,
    val creator: String? = null,
    val description: String? = null,
    val genre: String? = null,
    val trope: String? = null,
    val metadata: List<MetadataDto> = emptyList(),
)

@Serializable
class MetadataDto(
    val type: String,
    val value: String? = null,
)

@Serializable
class EpisodeEntryDto(
    val episode: EpisodeDto,
    val lockData: LockDto? = null,
)

@Serializable
class EpisodeDto(
    val id: String,
    val name: String,
    val sequence: Float,
    val releaseDate: Long? = null,
    val state: String? = null,
)

@Serializable
class LockDto(
    val isLocked: Boolean = false,
)

@Serializable
class PanelDto(
    val imageUrl: String,
    val sequence: Int,
)
