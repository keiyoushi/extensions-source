package eu.kanade.tachiyomi.extension.all.mangapark

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GraphQL<T>(
    val variables: T,
    val query: String,
)

@Serializable
data class SearchVariables(val select: SearchPayload)

@Serializable
data class SearchPayload(
    @SerialName("word") val query: String? = null,
    val incGenres: List<String>? = null,
    val excGenres: List<String>? = null,
    val incTLangs: List<String>? = null,
    val incOLangs: List<String>? = null,
    val sortby: String? = null,
    val chapCount: String? = null,
    val origStatus: String? = null,
    val siteStatus: String? = null,
    val page: Int,
    val size: Int,
)

@Serializable
data class IdVariables(val id: String)
