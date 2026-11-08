package eu.kanade.tachiyomi.extension.pt.instahentai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class GenreDto(val name: String, val slug: String)

@Serializable
class SchemaGraphDto(@SerialName("@graph") val graph: List<SchemaNodeDto>)

@Serializable
class SchemaNodeDto(@SerialName("@type") val type: String, val dateModified: String? = null)
