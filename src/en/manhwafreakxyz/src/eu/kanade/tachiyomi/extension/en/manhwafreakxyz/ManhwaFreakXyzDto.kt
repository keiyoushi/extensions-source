package eu.kanade.tachiyomi.extension.en.manhwafreakxyz

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class SearchDto(
    @SerialName("data")
    val mangas: List<MangaDto>,
)

@Serializable
class MangaDto(
    val thumbnail: String,
    val title: String,
    val url: String,
)
