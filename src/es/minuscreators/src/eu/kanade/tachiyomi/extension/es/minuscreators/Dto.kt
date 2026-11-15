package eu.kanade.tachiyomi.extension.es.minuscreators

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.tryParseDateTime
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

// Novels, books and artworks are left out
private val COMIC_TYPES = mapOf("comic" to "Cómic", "manga" to "Manga")

private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

private fun HttpUrl.image(path: String?) = path?.let { resolve(it)?.toString() }

@Serializable
class SearchDto(
    val obras: List<WorkDto>,
    val totalObras: Int,
)

@Serializable
class WorkDto(
    private val id: Int,
    private val titulo: String,
    private val portada: String?,
    private val tipoEntrega: String,
) {
    val isComic get() = tipoEntrega in COMIC_TYPES

    fun toSManga(baseUrl: HttpUrl) = SManga.create().apply {
        url = "/obra/$id"
        title = titulo
        thumbnail_url = baseUrl.image(portada)
    }
}

@Serializable
class RecentDto(val items: List<RecentItemDto>)

@Serializable
class RecentItemDto(
    private val obraId: Int,
    private val tituloObra: String,
    private val portada: String?,
    private val tipoEntrega: String,
) {
    val isComic get() = tipoEntrega in COMIC_TYPES

    fun toSManga(baseUrl: HttpUrl) = SManga.create().apply {
        url = "/obra/$obraId"
        title = tituloObra
        thumbnail_url = baseUrl.image(portada)
    }
}

@Serializable
class WorkDetailsDto(val obra: WorkDetailDto)

@Serializable
class WorkDetailDto(
    private val id: Int,
    private val titulo: String,
    private val descripcion: String?,
    private val genero: String?,
    private val tipoEntrega: String,
    private val serieConcluida: Boolean,
    private val portada: String?,
    private val autor: String?,
    private val capitulos: List<ChapterDto>,
) {
    val isComic get() = tipoEntrega in COMIC_TYPES

    fun toSManga(baseUrl: HttpUrl) = SManga.create().apply {
        url = "/obra/$id"
        title = titulo
        thumbnail_url = baseUrl.image(portada)
        author = autor
        val categories = genero.orEmpty().split(',').filter(String::isNotBlank).map { GENRES[it] ?: it }
        genre = (listOfNotNull(COMIC_TYPES[tipoEntrega]) + categories).joinToString().ifEmpty { null }
        status = if (serieConcluida) SManga.COMPLETED else SManga.ONGOING
        description = MarkdownDescription.Builder().description(descripcion).build()
    }

    fun toSChapters() = capitulos.sortedByDescending { it.numeroCapitulo }.map { it.toSChapter(id) }
}

@Serializable
class ChapterDto(
    val numeroCapitulo: Int,
    private val titulo: String?,
    private val creadoEn: String?,
    private val bloqueado: Boolean,
) {
    fun toSChapter(workId: Int) = SChapter.create().apply {
        url = "/obra/$workId/capitulo/$numeroCapitulo"
        name = buildString {
            if (bloqueado) append("🔒 ")
            append(titulo?.takeIf { it.isNotBlank() } ?: "Capítulo $numeroCapitulo")
        }
        chapter_number = numeroCapitulo.toFloat()
        // In UTC, like the Last-Modified of the uploaded files
        date_upload = DATE_FORMAT.tryParseDateTime(creadoEn, ZoneOffset.UTC)
    }
}

@Serializable
class ReaderDto(private val obra: ReaderWorkDto) {
    // The site's reader shows the chapter's cover first: its own, or the work's for the first chapter
    fun toPages(baseUrl: HttpUrl): List<Page> {
        val chapter = obra.capituloActual
        if (chapter.bloqueado) return emptyList()
        val cover = chapter.portadaLectura ?: chapter.miniatura ?: obra.portada.takeIf { chapter.numeroCapitulo == 1 }
        return (listOfNotNull(baseUrl.image(cover)) + obra.paginas.map { baseUrl.image(it.imagen)!! })
            .mapIndexed { index, url -> Page(index, imageUrl = url) }
    }
}

@Serializable
class ReaderWorkDto(
    val portada: String?,
    val capituloActual: CurrentChapterDto,
    val paginas: List<PageDto>,
)

@Serializable
class CurrentChapterDto(
    val numeroCapitulo: Int,
    val portadaLectura: String?,
    val miniatura: String?,
    val bloqueado: Boolean,
)

@Serializable
class PageDto(val imagen: String)
