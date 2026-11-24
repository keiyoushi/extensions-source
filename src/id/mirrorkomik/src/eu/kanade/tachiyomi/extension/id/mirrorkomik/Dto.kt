package eu.kanade.tachiyomi.extension.id.mirrorkomik

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

@Serializable
class LoadMoreDto(
    val html: String? = null,
    val lastId: JsonPrimitive? = null,
    val lastScore: JsonPrimitive? = null,
)

@Serializable
class GenreDto(
    val name: String,
    val slug: String,
)
