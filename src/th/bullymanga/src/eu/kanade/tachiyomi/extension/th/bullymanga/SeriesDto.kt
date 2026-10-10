package eu.kanade.tachiyomi.extension.th.bullymanga

import kotlinx.serialization.Serializable

@Serializable
class SeriesDto(
    val name: String? = null,
    val image: String? = null,
    val description: String? = null,
)
