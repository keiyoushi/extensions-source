package eu.kanade.tachiyomi.extension.pt.yugenmangas

import eu.kanade.tachiyomi.extension.pt.yugenmangas.YugenMangas.Companion.DATE_FORMAT
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import java.util.Calendar

@Serializable
class PageDto<T>(
    @SerialName("current_page")
    val page: Int,
    @SerialName("total_pages")
    val totalPages: Int,
    val results: List<T>,
) {
    fun hasNext() = page < totalPages
}

@Serializable
class MangaDto(
    @JsonNames("series_code")
    val code: String,
    @JsonNames("path_cover")
    val cover: String,
    @JsonNames("title", "series_name")
    val name: String,
) {
    fun toSManga(): SManga = SManga.create().apply {
        title = this@MangaDto.name
        thumbnail_url = cover
        url = "/series/$code"
    }
}

@Serializable
class LatestUpdatesDto(
    val series: List<MangaDto>,
)

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
class ChapterDto(
    val code: String,
    val name: String,
    @SerialName("upload_date")
    val date: String,
) {
    fun toSChapter(mangaCode: String): SChapter = SChapter.create().apply {
        name = this@ChapterDto.name
        date_upload = try { parseDate() } catch (_: Exception) { 0L }
        url = "/series/$mangaCode/$code"
    }

    private fun parseDate(): Long {
        return try {
            if ("dia" in date) {
                val number = Regex("""(\d+)""").find(date)?.value?.toIntOrNull() ?: return 0L
                return Calendar.getInstance().let {
                    it.apply { add(Calendar.DAY_OF_MONTH, -number) }.timeInMillis
                }
            }
            return DATE_FORMAT.parse(date)!!.time
        } catch (_: Exception) { 0L }
    }
}

@Serializable
class SeriesDto(
    val code: String,
)

@Serializable
class SearchDto(
    val query: String,
)

@Serializable
class PageListDto(
    val images: List<String>,
)
