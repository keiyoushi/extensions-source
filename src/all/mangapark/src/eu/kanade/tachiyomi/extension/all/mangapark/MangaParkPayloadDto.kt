package eu.kanade.tachiyomi.extension.all.mangapark

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class GraphQL<T>(
    private val variables: T,
    private val query: String,
)

@Serializable
class SearchVariables(private val select: SearchPayload)

@Serializable
class SearchPayload(
    @SerialName("word") private val query: String? = null,
    private val incGenres: List<String>? = null,
    private val excGenres: List<String>? = null,
    private val incTLangs: List<String>? = null,
    private val incOLangs: List<String>? = null,
    private val sortby: String? = null,
    private val chapCount: String? = null,
    private val origStatus: String? = null,
    private val siteStatus: String? = null,
    private val page: Int,
    private val size: Int,
)

@Serializable
class IdVariables(private val id: String)
