package eu.kanade.tachiyomi.extension.es.kumanga

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable

@Serializable
class ComicsPayloadDto(
    val contents: List<ComicDto>,
    val retrievedCount: Int,
)

@Serializable
class ComicDto(
    private val id: Int,
    private val name: String,
    private val slug: String,
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        title = name
        url = createMangaUrl(id.toString(), slug)
        thumbnail_url = guessMangaCover(id.toString(), baseUrl)
    }

    private fun guessMangaCover(mangaId: String, baseUrl: String) = "$baseUrl/kumathumb.php?src=$mangaId"
    private fun createMangaUrl(mangaId: String, mangaSlug: String) = "/manga/$mangaId/$mangaSlug"
}

@Serializable
class ImageDto(
    val imgURL: String,
)
