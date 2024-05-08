package eu.kanade.tachiyomi.extension.en.mangamo.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class TokenRefreshDto(
    @SerialName("expires_in")
    val expiresIn: Long,
    @SerialName("id_token")
    val idToken: String,
    @SerialName("refresh_token")
    val refreshToken: String,
)
