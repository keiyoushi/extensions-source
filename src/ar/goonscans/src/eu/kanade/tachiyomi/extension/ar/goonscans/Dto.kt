package eu.kanade.tachiyomi.extension.ar.goonscans

import kotlinx.serialization.Serializable

@Serializable
class LiveSearchDto(
    val data: LiveSearchDataDto,
)

@Serializable
class LiveSearchDataDto(
    val html: String,
)
