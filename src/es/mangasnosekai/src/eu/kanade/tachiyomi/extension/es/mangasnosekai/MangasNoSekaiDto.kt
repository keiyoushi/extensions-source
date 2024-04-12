package eu.kanade.tachiyomi.extension.es.mangasnosekai

import eu.kanade.tachiyomi.source.model.SChapter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

@Serializable
data class PayloadDto(
    val manga: List<MangaDto>,
)

@Serializable
data class MangaDto(
    val chapters: List<ChapterDto>,
)

private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale("es"))

@Serializable
data class ChapterDto(
    @SerialName("chapter_name") private val name: String,
    @SerialName("chapter_slug") private val slug: String,
    @SerialName("date_gmt") private val date: String,
) {

    fun toSChapter(mangaSlug: String) = SChapter.create().apply {
        name = this@ChapterDto.name
        url = "$mangaSlug/$slug"
        date_upload = try {
            dateFormat.parse(date)?.time ?: 0
        } catch (e: ParseException) {
            0
        }
    }
}
