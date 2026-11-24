package eu.kanade.tachiyomi.extension.es.mangashiina

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
class CatalogDto(
    val mangas: List<MangaDto>,
)

@Serializable
class MangaDto(
    private val id: Int,
    val titulo: String,
    private val portada: String? = null,
    val tipo: String? = null,
    val genres: List<String> = emptyList(),
    val totalViews: Long = 0,
    @SerialName("latest_update_at") val latestUpdateAt: String? = null,
    @SerialName("published_at") val publishedAt: String? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = id.toString()
        title = titulo
        thumbnail_url = portada
    }
}

@Serializable
class DetailsDto(
    private val titulo: String,
    @SerialName("titulo_original") private val tituloOriginal: String? = null,
    private val portada: String? = null,
    private val tipo: String? = null,
    private val genres: List<String> = emptyList(),
    private val descripcion: String? = null,
    private val status: String? = null,
) {
    fun toSManga(mangaUrl: String) = SManga.create().apply {
        url = mangaUrl
        title = titulo
        thumbnail_url = portada
        genre = (listOfNotNull(tipo) + genres).distinct().joinToString().ifEmpty { null }
        // The site repeats the title as the first line of the synopsis
        val synopsis = descripcion?.replace("\r\n", "\n")?.removePrefix(titulo)?.trim()?.ifEmpty { null }
        val originalTitle = tituloOriginal?.takeIf(String::isNotBlank)?.let { "Título original: $it" }
        description = listOfNotNull(synopsis, originalTitle).joinToString("\n\n").ifEmpty { null }
        status = when (this@DetailsDto.status?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }
}

@Serializable
class ChaptersDto(
    val chapters: List<ChapterDto>,
)

@Serializable
class ChapterDto(
    private val id: Int,
    @SerialName("chapter_number") private val chapterNumber: Float,
    private val title: String,
    @SerialName("created_at") private val createdAt: String?,
    @SerialName("is_paid") private val isPaid: Boolean,
) {
    fun toSChapter() = SChapter.create().apply {
        url = id.toString()
        name = if (isPaid) "🔒 $title" else title
        chapter_number = chapterNumber
        date_upload = Instant.tryParse(createdAt)
    }
}

@Serializable
class PagesDto(
    val images: List<String> = emptyList(),
)

@Serializable
class FilterData(
    val types: List<String>,
    val genres: List<String>,
)

@Serializable
class IdDto(
    val id: Int,
)

@Serializable
class SeriesRefDto(
    @SerialName("ero_seri") val eroSeri: String,
)
