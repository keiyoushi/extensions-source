package eu.kanade.tachiyomi.extension.es.onfmangas

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.utils.tryParseDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jsoup.parser.Parser
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)

@Serializable
class ChapterDto(
    private val url: String,
    @SerialName("titulo_str") private val displayTitle: String? = null,
    private val titulo: String? = null,
    private val numero: String,
    @SerialName("fecha_subida") val date: String? = null,
    @SerialName("grupos_list") private val groups: List<GroupDto>? = null,
    @SerialName("otras_versiones") private val otherVersions: List<ChapterDto>? = null,
) {
    val numberFloat: Float get() = numero.toFloat()

    fun toSChapters(onePerGroup: Boolean): List<SChapter> {
        val versions = (listOf(this) + otherVersions.orEmpty()).map { it.toSChapter(this) }
        return if (onePerGroup) versions.distinctBy { it.scanlator } else versions
    }

    private fun toSChapter(parent: ChapterDto) = SChapter.create().apply {
        url = this@ChapterDto.url
        name = chapterName(parent)
        chapter_number = numberFloat
        date_upload = dateFormat.tryParseDateTime(date, ZoneOffset.UTC)
        scanlator = groups?.joinToString(" & ") { it.nombre }?.ifEmpty { null }
    }

    // The site's reader leaves out the number when the title contains "cap" or "ch."
    private fun chapterName(parent: ChapterDto): String {
        val number = "Capítulo ${numberFloat.toString().removeSuffix(".0")}"
        val title = listOf(displayTitle, titulo, parent.displayTitle)
            .firstOrNull { !it.isNullOrBlank() }
            ?.let { Parser.unescapeEntities(it, false).trim() }
            ?: return number
        val lowerTitle = title.lowercase()
        return if ("cap" in lowerTitle || "ch." in lowerTitle) title else "$number: $title"
    }
}

@Serializable
class GroupDto(
    val nombre: String,
)

@Serializable
class GenreDto(
    val id: String,
    val name: String,
)

@Serializable
class PageDto(
    private val src: String,
    private val fallback: String? = null,
) {
    fun toPage(index: Int) = Page(
        index = index,
        // The MangaDex node in src answers 404, fallback is the MangaDex origin
        imageUrl = fallback?.ifEmpty { null } ?: src,
    )
}
