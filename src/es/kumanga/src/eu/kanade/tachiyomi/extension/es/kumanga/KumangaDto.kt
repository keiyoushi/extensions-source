package eu.kanade.tachiyomi.extension.es.kumanga

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable

@Serializable
class KumangaSearchResponseDto(
    val contents: List<KumangaMangaDto> = emptyList(),
)

@Serializable
class KumangaMangaDto(
    val id: Int,
    val name: String,
    val slug: String,
    val description: String? = null,
    val categories: List<KumangaCategoryDto>? = null,
) {
    fun toSManga(baseUrl: String): SManga = SManga.create().apply {
        title = name
        url = "/manga/$id/$slug"
        thumbnail_url = "https://static.kumanga.com/manga/${id / 1000}/$id.jpg"
        this@KumangaMangaDto.description?.let { description = it }
        genre = categories?.joinToString { it.name }
    }
}

@Serializable
class KumangaCategoryDto(
    val id: Int,
    val name: String,
)

@Serializable
class KumangaImageDto(
    val imgURL: String? = null,
)

@Serializable
class KumangaOtherChapterDto(
    val NumCap: String? = null,
    val title: String? = null,
)
