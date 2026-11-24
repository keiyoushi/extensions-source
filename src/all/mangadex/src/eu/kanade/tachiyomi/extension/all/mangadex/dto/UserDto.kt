package eu.kanade.tachiyomi.extension.all.mangadex.dto

import eu.kanade.tachiyomi.extension.all.mangadex.MDConstants
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName(MDConstants.USER)
class UserDto(override val attributes: UserAttributes? = null) : EntityDto()

@Serializable
class UserAttributes(val username: String) : AttributesDto()
