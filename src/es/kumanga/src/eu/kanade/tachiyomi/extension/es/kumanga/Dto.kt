package eu.kanade.tachiyomi.extension.es.kumanga

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class SearchResponseDto(
    val contents: List<MangaDto>,
    val totalContents: Int,
)

@Serializable
class MangaDto(
    private val id: Int,
    private val name: String,
    private val slug: String,
) {
    fun toSManga() = SManga.create().apply {
        url = "/manga/$id/$slug"
        title = name
        thumbnail_url = coverUrl(id)
    }
}

@Serializable
class OtherChapterDto(
    @SerialName("NumCap") private val number: String,
    private val title: String? = null,
) {
    fun toSChapter(mangaId: Int) = SChapter.create().apply {
        url = "/manga/$mangaId/capitulo/$number"
        name = buildString {
            append("Capítulo ", number)
            if (!title.isNullOrBlank()) append(": ", title)
        }
    }
}

fun coverUrl(id: Int) = "https://static.kumanga.com/manga/${(id + 2499) / 2500}/$id.jpg"
