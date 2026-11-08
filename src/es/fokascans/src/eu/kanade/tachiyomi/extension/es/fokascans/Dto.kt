package eu.kanade.tachiyomi.extension.es.fokascans

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.Serializable
import java.time.format.DateTimeFormatter

@Serializable
class LibraryDto(
    val library: List<EntryDto>,
)

@Serializable
class EntryDto(
    val id: String,
    private val title: String,
    private val type: String,
    private val altTitles: List<String> = emptyList(),
    private val cover: String? = null,
    private val tags: List<String> = emptyList(),
    private val status: String? = null,
    private val synopsis: String? = null,
    private val chapters: List<ChapterDto> = emptyList(),
) {
    val isReadable get() = type == "manga" || type == "sbs"

    private val url get() = "/${if (type == "sbs") "sbs" else "manga"}/$id"

    fun matches(query: String) = (altTitles + title).any { it.contains(query.trim(), ignoreCase = true) }

    fun toSManga() = SManga.create().apply {
        url = this@EntryDto.url
        title = this@EntryDto.title
        thumbnail_url = cover
        genre = tags.joinToString().ifEmpty { null }
        description = MarkdownDescription.Builder()
            .description(synopsis)
            .altTitles(altTitles)
            .build()
        status = this@EntryDto.status.orEmpty().lowercase().let {
            when {
                "emisión" in it -> SManga.ONGOING
                "finalizado" in it -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }
    }

    // Newest first, like the site's chapter list
    fun toSChapterList() = chapters.sortedByDescending { it.number?.toFloatOrNull() }.map { chapter ->
        SChapter.create().apply {
            url = "${this@EntryDto.url}/${chapter.id}"
            name = if (type == "sbs") chapter.title!! else chapter.name
            chapter.number?.toFloatOrNull()?.let { chapter_number = it }
            date_upload = DateTimeFormatter.ISO_LOCAL_DATE.tryParseDate(chapter.releasedAt)
        }
    }

    fun pages(chapterId: String) = chapters.first { it.id == chapterId }.pages
}

@Serializable
class ChapterDto(
    val id: String,
    val number: String? = null,
    val title: String? = null,
    val releasedAt: String? = null,
    val pages: List<String> = emptyList(),
) {
    val name get() = buildString {
        append(if (number != null) "Cap. $number" else "Capítulo")
        title?.let { append(": ", it) }
    }
}
