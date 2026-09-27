package eu.kanade.tachiyomi.extension.all.namicomi.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@Serializable
data class Token(
    @SerialName("id_token") val idToken: String,
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    val scope: String,
    @SerialName("expires_at") private val expiresAt: Long,
    @SerialName("refresh_expires_at") private val refreshExpiresAt: Long? = null,
) {
    val expires get() = Instant.fromEpochSeconds(expiresAt)
    val refreshExpires get() = refreshExpiresAt?.let { Instant.fromEpochSeconds(it) }
}

@Serializable
class RefreshTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in") private val accessTokenExpiresIn: Long,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("refresh_expires_in") private val refreshTokenExpiresIn: Long? = null,
) {
    val expires get() = Clock.System.now().plus(accessTokenExpiresIn.seconds)
    val refreshExpires get() = refreshTokenExpiresIn?.let { Clock.System.now().plus(it.seconds) }
}
