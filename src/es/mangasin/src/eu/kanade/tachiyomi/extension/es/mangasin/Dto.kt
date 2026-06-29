package eu.kanade.tachiyomi.extension.es.mangasin

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class CDT(val ct: String, val s: String)

@Serializable
class Chapter(
    val slug: String,
    val name: String,
    val number: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
class LatestManga(
    @SerialName("manga_name") val name: String,
    @SerialName("manga_slug") val slug: String,
)

@Serializable
class LatestUpdateResponse(
    val data: List<LatestManga>,
    val totalPages: Int,
)
