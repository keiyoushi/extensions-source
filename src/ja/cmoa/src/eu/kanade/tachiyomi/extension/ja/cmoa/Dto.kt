package eu.kanade.tachiyomi.extension.ja.cmoa

import kotlinx.serialization.Serializable

@Serializable
class ApiTitlesResponse(
    val data: List<ApiTitle>,
)

@Serializable
class ApiTitle(
    val name: String,
    val url: String,
    val thumbnail: String,
)
