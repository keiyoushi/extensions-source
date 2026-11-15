package eu.kanade.tachiyomi.extension.ja.comicgrast

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class ComicData(
    @SerialName("serial_comic_id") val serialComicId: Int,
    @SerialName("story_number") val storyNumber: Int,
)

@Serializable
class ComicPage(
    val name: String,
    val seed: String,
    val size: Int,
)
