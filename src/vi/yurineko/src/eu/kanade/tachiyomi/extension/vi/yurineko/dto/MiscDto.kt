package eu.kanade.tachiyomi.extension.vi.yurineko.dto

import kotlinx.serialization.Serializable

@Serializable
class ErrorResponseDto(
    val message: String? = null,
)

@Serializable
class UserDto(
    val token: String,
)
