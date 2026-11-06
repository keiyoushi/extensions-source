package eu.kanade.tachiyomi.extension.pt.megahentai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class SchemaGraph(
    @SerialName("@graph") val graph: List<SchemaNode>,
)

@Serializable
class SchemaNode(
    val dateModified: String? = null,
)
