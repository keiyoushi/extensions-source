package eu.kanade.tachiyomi.extension.pt.yugenmangas

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.Calendar

@Serializable
class MangaDetailsDto(
    val title: String,
    @SerialName("path_cover")
    val cover: String,
    val code: String,
    val author: String? = null,
    val artist: String? = null,
    val genres: List<String> = emptyList(),
    val synopsis: String? = null,
    val status: String? = null,
) {
    fun toSManga(): SManga = SManga.create().apply {
        title = this@MangaDetailsDto.title
        author = this@MangaDetailsDto.author
        artist = this@MangaDetailsDto.artist
        description = synopsis
        status = when (this@MangaDetailsDto.status) {
            "Em Lançamento" -> SManga.ONGOING
            "Hiato" -> SManga.ON_HIATUS
            "Cancelado" -> SManga.CANCELLED
            "Finalizado" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        genre = genres.joinToString()
        thumbnail_url = cover
        url = "/series/$code"
    }
}

@Serializable
class ContainerDto(
    val chapters: List<ChapterDto>,
    val currentPage: Int,
    val series: MangaDetailsDto,
    val totalPages: Int,
) {
    fun hasNext() = currentPage < totalPages

    fun toSChapterList() = chapters.map { it.toSChapter(series.code) }
}

@Serializable
class ChapterDto(
    val code: String,
    val name: String,
    @SerialName("upload_date")
    val date: String,
) {
    fun toSChapter(mangaCode: String): SChapter = SChapter.create().apply {
        name = this@ChapterDto.name
        date_upload = parseDate()
        url = "/series/$mangaCode/$code"
    }

    private fun parseDate(): Long {
        return try {
            val number = Regex("""(\d+)""").find(date)?.value?.toIntOrNull() ?: return 0L
            Calendar.getInstance().let {
                when {
                    date.contains("dia") -> it.apply { add(Calendar.DAY_OF_MONTH, -number) }.timeInMillis
                    date.contains("mês", "meses") -> it.apply { add(Calendar.MONTH, -number) }.timeInMillis
                    date.contains("ano") -> it.apply { add(Calendar.YEAR, -number) }.timeInMillis
                    else -> 0L
                }
            }
        } catch (_: Exception) { 0L }
    }

    private fun String.contains(vararg elements: String): Boolean {
        return elements.any { this.contains(it, true) }
    }
}

@Serializable
class SearchDto(
    val query: String,
)

@Serializable
class SearchMangaDto(
    val series: List<MangaDto>,
)

@Serializable
class MangaDto(
    val code: String,
    val cover: String,
    val name: String,
) {
    fun toSManga() = SManga.create().apply {
        title = name
        thumbnail_url = cover
        url = "/series/$code"
    }
}
