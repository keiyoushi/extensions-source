package eu.kanade.tachiyomi.extension.vi.damconuong

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class PagesResponse(
    @SerialName("e") val encrypted: String,
)

@Serializable
class PagesPayload(
    @SerialName("p") val pages: List<String> = emptyList(),
    @SerialName("s") val scrambleKeys: List<String?>? = null,
)

@Serializable
class GenreOption(
    val id: Int,
    val name: String,
)
