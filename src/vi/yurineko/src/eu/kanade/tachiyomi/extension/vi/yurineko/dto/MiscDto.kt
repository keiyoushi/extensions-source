package eu.kanade.tachiyomi.extension.vi.yurineko.dto
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponseDto(
    val message: String? = null,
)

@Serializable
data class UserDto(
    val id: Int,
    val name: String,
    val email: String,
    val avatar: String,
    val role: Int,
    val money: Int,
    val username: String,
    val isBanned: Int,
    val isPremium: Int,
    val token: String,
)
