package eu.kanade.tachiyomi.extension.es.ranoa

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlin.time.Instant

@Serializable
class CatalogDto(val obras: List<WorkDto>)

@Serializable
class WorkDto(
    val key: String,
    val titulo: String,
    private val imagen: String?,
    val contenido: JsonElement,
) {
    fun toSManga() = SManga.create().apply {
        url = key
        title = titulo
        thumbnail_url = imagen
    }
}

@Serializable
class ChapterDto(
    private val number: Double,
    private val title: String,
    private val publishedAt: String?,
) {
    // The reader's path is the volume's one, with the chapter after the volume
    fun toSChapter(volumePath: String) = SChapter.create().apply {
        val numberText = number.toString().removeSuffix(".0")
        url = volumePath.replace("/tomo/", "/leer/") + "/" + numberText
        name = if (title.startsWith("Capítulo")) title else "Capítulo $numberText: $title"
        chapter_number = number.toFloat()
        date_upload = Instant.tryParse(publishedAt)
    }
}

@Serializable
class ReaderDto(
    private val chapterId: String,
    val pages: List<PageDto>,
)

@Serializable
class PageDto(val imageUrl: String)
