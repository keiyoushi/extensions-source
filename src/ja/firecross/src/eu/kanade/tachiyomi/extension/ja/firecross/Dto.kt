package eu.kanade.tachiyomi.extension.ja.firecross

import kotlinx.serialization.Serializable

@Serializable
class ChapterId(
    val token: String,
    val id: String,
)

@Serializable
class ApiResponse(
    val redirect: String,
)
