package eu.kanade.tachiyomi.extension.es.mangasin

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CDT(val ct: String, val s: String)

@Serializable
data class Chapter(
    val slug: String,
    val name: String,
    val number: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class SearchResult(
    @SerialName("value") val name: String,
    @SerialName("data") val slug: String,
)
