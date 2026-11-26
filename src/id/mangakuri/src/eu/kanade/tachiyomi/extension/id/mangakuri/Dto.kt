package eu.kanade.tachiyomi.extension.id.mangakuri

import kotlinx.serialization.Serializable

@Serializable
class LoginRequestDto(
    private val email: String,
    private val password: String,
)

@Serializable
class LoginResponseDto(
    val token: String,
)

@Serializable
class JwtPayloadDto(
    val exp: Long,
)
