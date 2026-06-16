package eu.kanade.tachiyomi.extension.ja.bookwalkerjp

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("normal")
class SingleDto(
    val uuid: String,
) : HoldBookEntityDto()
