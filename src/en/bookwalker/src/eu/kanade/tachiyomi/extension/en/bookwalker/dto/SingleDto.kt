package eu.kanade.tachiyomi.extension.en.bookwalker.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("normal")
class SingleDto(
    val uuid: String,
) : HoldBookEntityDto()
