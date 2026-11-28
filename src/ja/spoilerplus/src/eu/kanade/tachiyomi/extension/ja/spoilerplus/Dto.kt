package eu.kanade.tachiyomi.extension.ja.spoilerplus

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

@Serializable
class ChapterImagesRequest(
    @SerialName("m") private val mangaId: Int,
    @SerialName("n") private val chapterNumber: JsonPrimitive,
)

@Serializable
class ChapterImagesDto(
    @SerialName("c") val cellOrder: String,
    @SerialName("d") val imageBase: String,
    @SerialName("e") val paths: List<String>,
)

@Serializable
class FilterData(
    val genres: List<Pair<String, String>>,
    val tags: List<Pair<String, String>>,
)
