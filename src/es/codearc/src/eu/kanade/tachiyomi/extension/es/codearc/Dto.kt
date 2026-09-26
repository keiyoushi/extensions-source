package eu.kanade.tachiyomi.extension.es.codearc

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl

@Serializable
class SearchResponseDto(
    val items: List<SearchItemDto>,
)

@Serializable
class SearchItemDto(
    private val slug: String,
    private val titulo: String,
    private val portada: String? = null,
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        url = "/$slug"
        title = titulo
        thumbnail_url = portada?.let { if (it.startsWith("http")) it else "$baseUrl$it" }
    }
}

@Serializable
class RelatedResponseDto(
    val items: List<RelatedItemDto>,
)

@Serializable
class RelatedItemDto(
    private val href: String,
    private val portada: String,
    private val titulo: String,
) {
    fun toSManga() = SManga.create().apply {
        url = href.toHttpUrl().encodedPath
        title = titulo
        thumbnail_url = portada
    }
}

@Serializable
class PageDto(
    val orden: Int,
    @SerialName("imagen_url") val imagenUrl: String,
)

@Serializable
class ReaderPagesDto(
    val items: List<PageDto>,
    val total: Int,
)

@Serializable
class DetailsResponseDto(
    private val mangaSlugValue: String,
    private val titulo: String,
    private val portadaUrl: String,
    private val estadoKey: String,
    private val artistaData: List<NameDto>,
    private val tagsData: List<NameDto>,
    private val sipnosis: String,
    private val chapters: List<ChapterDto>,
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        title = titulo
        description = sipnosis
        thumbnail_url = "https://cdn.codearctraducciones.com${portadaUrl.removePrefix("/uploads")}"
        genre = tagsData.joinToString { it.name }.ifEmpty { null }

        artist = artistaData.joinToString { it.name }.ifEmpty { null }
        author = artistaData.joinToString { it.name }.ifEmpty { null }

        status = when (estadoKey) {
            "FINALIZADO" -> SManga.COMPLETED
            "EN_EMISION" -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }
    }

    fun toSChapterList() = chapters.map { chapter ->
        SChapter.create().apply {
            url = "/reader/$mangaSlugValue/${chapter.numero}/cascade"
            name = chapter.titulo?.takeUnless { it == titulo } ?: "Capítulo ${chapter.numero}"
            chapter_number = chapter.numero.toFloatOrNull() ?: -1f
        }
    }
}

@Serializable
class NameDto(val name: String)

@Serializable
class ChapterDto(
    val numero: String,
    val titulo: String? = null,
)
