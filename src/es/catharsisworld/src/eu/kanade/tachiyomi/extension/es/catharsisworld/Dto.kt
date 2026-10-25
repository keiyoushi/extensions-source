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
    @SerialName("requires_login") val requiresLogin: Boolean = false,
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
class PagesDto(
    val paginas: List<PageDto>,
    val chapterSessionToken: String,
)

@Serializable
class PageDto(
    val pageId: String,
    val id: String,
    val index: Int,
)

@Serializable
class PageTicketRequest(
    private val pageIndex: Int,
    private val sessionToken: String,
)

@Serializable
class PageTicketResponse(
    val signedToken: String,
    private val pageId: String,
    val scrambleKey: String? = null,
    val xorByte: Int,
)

@Serializable
class ChapterTokenPayload(
    val cid: Int,
    val exp: Long,
)

@Serializable
class SystemDto(
    @SerialName("cap_sitekey") val capSitekey: String,
    @SerialName("cap_api_endpoint") val capApiEndpoint: String,
)

@Serializable
class CapChallengeResponse(
    val challenge: CapChallenge,
    val token: String,
)

@Serializable
class CapChallenge(
    @SerialName("c") val count: Int,
    @SerialName("s") val saltSize: Int,
    @SerialName("d") val difficulty: Int,
)

@Serializable
class CapRedeemRequest(
    private val token: String,
    private val solutions: List<Int>,
)

@Serializable
class CapRedeemResponse(
    val success: Boolean,
    val token: String? = null,
)

@Serializable
class ApiErrorDto(
    val code: String? = null,
)

@Serializable
class ReaderPassRequest(
    private val token: String,
    private val sitekey: String,
)

@Serializable
class ReaderPassDto(
    val pass: String,
    val expiresAt: Long,
)
