package eu.kanade.tachiyomi.extension.all.mangadex.dto

import eu.kanade.tachiyomi.extension.all.mangadex.MDConstants
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName(MDConstants.user)
data class UserDto(override val attributes: UserAttributes? = null) : EntityDto()

@Serializable
data class UserAttributes(val username: String) : AttributesDto()
