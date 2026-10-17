package eu.kanade.tachiyomi.multisrc.guya

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class SeriesDto(
    val slug: String,
    val title: String? = null,
    val author: String? = null,
    val artist: String? = null,
    val description: String? = null,
    val cover: String? = null,
    @SerialName("last_updated") val lastUpdated: Long? = null,
)

@Serializable
class SeriesDetailsDto(
    val slug: String,
    val title: String,
    val author: String? = null,
    val artist: String? = null,
    val description: String? = null,
    val cover: String? = null,
    val groups: Map<String, String>,
    val chapters: Map<String, ChapterDto>,
    @SerialName("preferred_sort") val preferredSort: List<String>? = null,
)

@Serializable
class ChapterDto(
    val title: String,
    val folder: String,
    val groups: Map<String, List<String>>,
    @SerialName("release_date") val releaseDate: Map<String, Long>? = null,
    @SerialName("preferred_sort") val preferredSort: List<String>? = null,
)
