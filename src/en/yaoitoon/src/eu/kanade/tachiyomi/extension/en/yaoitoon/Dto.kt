package eu.kanade.tachiyomi.extension.en.yaoitoon

import kotlinx.serialization.Serializable

@Serializable
class Dto(
    val status: Boolean = false,
    val html: String = "",
)
