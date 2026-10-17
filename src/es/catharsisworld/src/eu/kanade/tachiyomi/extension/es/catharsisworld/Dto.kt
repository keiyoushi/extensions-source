package eu.kanade.tachiyomi.extension.es.catharsisworld

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
class MangaListDto(
    val data: List<MangaDto>,
    @SerialName("total_pages") val totalPages: Int,
)

@Serializable
class MangaDto(
    private val id: Int,
    private val nombre: String,
    @SerialName("portada_url") private val portadaUrl: String? = null,
    private val descripcion: String? = null,
    private val estado: String? = null,
    private val tipo: String? = null,
    @SerialName("nombre_alt_1") private val nombreAlt1: String? = null,
    @SerialName("nombre_alt_2") private val nombreAlt2: String? = null,
    @SerialName("fk_generos") private val generos: List<MangaGenreDto> = emptyList(),
    val capitulos: List<ChapterDto> = emptyList(),
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        url = id.toString()
        title = nombre
        thumbnail_url = portadaUrl?.let { "$baseUrl/assets/$it" }
    }

    fun toSMangaDetails(baseUrl: String) = toSManga(baseUrl).apply {
        genre = (listOfNotNull(tipo) + generos.map { it.genre.nombre }).joinToString()
        description = buildString {
            descripcion?.let { append(it.trim()) }
            val altNames = listOfNotNull(nombreAlt1, nombreAlt2).filter(String::isNotBlank)
            if (altNames.isNotEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append("Nombres alternativos:\n", altNames.joinToString("\n"))
            }
        }
        status = when (estado) {
            "curso" -> SManga.ONGOING
            "pausado" -> SManga.ON_HIATUS
            "terminado" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }
}

@Serializable
class MangaGenreDto(
    @SerialName("generos_id") val genre: GenreDto,
)

@Serializable
class GenreDto(
    val id: String,
    val nombre: String,
)

@Serializable
class ChapterDto(
    private val numero: Double,
    private val titulo: String? = null,
    val estado: String? = null,
    @SerialName("fecha_publicacion") private val fechaPublicacion: String? = null,
    @SerialName("date_created") private val dateCreated: String? = null,
) {
    val number get() = numero

    fun toSChapter(mangaId: String) = SChapter.create().apply {
        val num = numero.toString().removeSuffix(".0")
        url = "$mangaId/$num"
        name = titulo?.takeIf(String::isNotBlank) ?: "Capítulo $num"
        chapter_number = numero.toFloat()
        date_upload = Instant.tryParse(fechaPublicacion ?: dateCreated)
    }
}

@Serializable
class PagesDto(val paginas: List<String>)
