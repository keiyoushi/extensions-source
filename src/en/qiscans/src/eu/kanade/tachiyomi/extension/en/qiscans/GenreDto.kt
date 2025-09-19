package eu.kanade.tachiyomi.extension.en.qiscans

import kotlinx.serialization.Serializable

@Serializable
class GenreDto(val id: Int, val name: String)

@Serializable
class PageDto(val url: String, val order: Int)
