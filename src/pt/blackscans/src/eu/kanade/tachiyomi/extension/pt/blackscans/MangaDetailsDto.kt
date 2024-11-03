package eu.kanade.tachiyomi.extension.pt.blackscans

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@Serializable
class MangaDetailsDto(
    val title: String,
    val artist: String,
    val author: String,
    val code: String,
    val genres: List<String>,
    @SerialName("path_cover")
    val cover: String,
    val status: String,
    val synopsis: String,
)

@Serializable
class MangaDto(
    val code: String,
    val title: String,
    @JsonNames("path_cover")
    val cover: String,
)

@Serializable
class ChapterList(
    val chapters: List<Chapter>,
)

@Serializable
data class Chapter(
    val code: String,
    val name: String,
    @SerialName("upload_date")
    val uploadAt: String,
)

@Serializable
class PagesDto(
    val images: List<String>,
)
