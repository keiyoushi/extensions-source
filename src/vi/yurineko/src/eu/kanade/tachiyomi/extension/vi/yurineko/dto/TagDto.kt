package eu.kanade.tachiyomi.extension.vi.yurineko.dto

import kotlinx.serialization.Serializable

@Serializable
class TagDto(
    val name: String,
)

@Serializable
class Author(
    val name: String?,
)

@Serializable
class Team(
    val name: String?,
)
