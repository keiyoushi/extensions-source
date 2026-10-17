package eu.kanade.tachiyomi.extension.ar.rocksmanga

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
class GenresDto(val genres: List<GenreDto>) {
    @Serializable
    class GenreDto(val name: String, val slug: String)
}

@Serializable
class DetailsDto(val manga: MangaDto) {
    @Serializable
    class MangaDto(
        private val title: String,
        @SerialName("alternative_titles") private val alternativeTitles: String? = null,
        private val synopsis: String? = null,
        @SerialName("cover_url") private val coverUrl: String? = null,
        private val status: String? = null,
        private val authors: List<NameDto> = emptyList(),
        private val artists: List<NameDto> = emptyList(),
        private val genres: List<NameDto> = emptyList(),
        private val slug: String,
    ) {
        fun toSManga(apiUrl: String) = SManga.create().apply {
            url = "/manga/$slug"
            title = this@MangaDto.title
            author = authors.joinToString { it.name }.ifEmpty { null }
            artist = artists.joinToString { it.name }.ifEmpty { null }
            description = buildString {
                synopsis?.let { append(it) }
                alternativeTitles?.takeIf { it.isNotBlank() }?.let {
                    if (isNotEmpty()) append("\n\n")
                    append("أسماء أخرى: ", it)
                }
            }.ifEmpty { null }
            genre = genres.joinToString { it.name }.ifEmpty { null }
            thumbnail_url = coverUrl?.let { "$apiUrl$it" }
            status = when (this@MangaDto.status) {
                "RELEASING" -> SManga.ONGOING
                "COMPLETED" -> SManga.COMPLETED
                "CANCELED" -> SManga.CANCELLED
                "ON_HOLD" -> SManga.ON_HIATUS
                else -> SManga.UNKNOWN
            }
        }
    }

    @Serializable
    class NameDto(val name: String)
}

@Serializable
class ChapterListDto(val mangaSlug: String, val chapters: List<ChapterDto>) {
    @Serializable
    class ChapterDto(
        val id: Int,
        private val number: String,
        private val title: String? = null,
        @SerialName("created_at") private val createdAt: String? = null,
    ) {
        fun toSChapter(slug: String) = SChapter.create().apply {
            url = "/manga/$slug/chapter/$number"
            name = "الفصل $number" + (title?.takeIf { it.isNotBlank() }?.let { " - $it" } ?: "")
            chapter_number = number.toFloatOrNull() ?: -1f
            date_upload = Instant.tryParse(createdAt)
        }
    }
}

@Serializable
class ReaderDto(val chapter: ChapterDto) {
    @Serializable
    class ChapterDto(val pages: List<String>)
}
