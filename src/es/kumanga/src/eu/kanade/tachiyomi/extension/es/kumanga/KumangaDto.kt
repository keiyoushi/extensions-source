package eu.kanade.tachiyomi.extension.es.kumanga

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

@Serializable
class KumangaSearchResponseDto(
    val contents: List<KumangaMangaDto> = emptyList(),
)

@Serializable
class KumangaMangaDto(
    val id: JsonPrimitive,
    val name: String,
    val slug: String,
    val description: String? = null,
    val categories: List<KumangaCategoryDto>? = null,
) {
    fun toSManga(baseUrl: String): SManga = SManga.create().apply {
        title = name
        val idStr = id.content
        url = "/manga/$idStr/$slug"
        val idInt = idStr.toIntOrNull() ?: 0
        thumbnail_url = "https://static.kumanga.com/manga/${(idInt / 2500) + 1}/$idStr.jpg"
        this@KumangaMangaDto.description?.let { description = it }
        genre = categories?.joinToString { it.name }
    }
}

@Serializable
class KumangaCategoryDto(
    val id: JsonPrimitive,
    val name: String,
)

@Serializable
class KumangaImageDto(
    @SerialName("imgURL") val imgUrl: String? = null,
)

@Serializable
class KumangaOtherChapterDto(
    @SerialName("NumCap") val numCap: JsonPrimitive? = null,
    val title: String? = null,
)
