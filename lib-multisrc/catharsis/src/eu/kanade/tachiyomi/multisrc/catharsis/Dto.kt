package eu.kanade.tachiyomi.multisrc.catharsis

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl

@Serializable
class MangaListDto(
    val data: List<MangaDto>,
    @SerialName("total_pages") val totalPages: Int,
)

@Serializable
class MangaDto(
    val id: Int,
    val slug: String,
    private val nombre: String,
    @SerialName("portada_url") private val portadaUrl: String? = null,
    private val descripcion: String? = null,
    private val estado: String? = null,
    private val tipo: String? = null,
    private val nsfw: Boolean = false,
    @SerialName("nombre_alt_1") private val nombreAlt1: String? = null,
    @SerialName("nombre_alt_2") private val nombreAlt2: String? = null,
    @SerialName("fk_generos") private val generos: List<MangaGenreDto> = emptyList(),
    @SerialName("fk_scans") private val scan: ScanDto? = null,
) {
    val scanlator get() = scan?.nombre

    fun toSManga(mangaKey: String, baseUrl: String) = SManga.create().apply {
        url = mangaKey
        title = nombre
        thumbnail_url = portadaUrl?.let { baseUrl.toHttpUrl().resolve("/assets/$it")?.toString() }
        genre = buildList {
            tipo?.let { add(typeName(it)) }
            addAll(generos.map { it.genre.nombre })
            if (nsfw) add("+18")
        }.distinct().joinToString().takeIf { it.isNotEmpty() }
        description = MarkdownDescription.Builder()
            // The site prints "None" for series without a synopsis
            .description(descripcion?.takeUnless { it == "None" })
            .altTitles(listOfNotNull(nombreAlt1, nombreAlt2), "Nombres alternativos")
            .build()
        status = when (estado?.lowercase()) {
            "curso", "ongoing" -> SManga.ONGOING
            "pausado" -> SManga.ON_HIATUS
            "terminado" -> SManga.COMPLETED
            "abandonado" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }

    private fun typeName(tipo: String) = when (val type = tipo.lowercase()) {
        "manwha" -> "Manhwa"
        else -> type.replaceFirstChar(Char::uppercase)
    }
}

// The details response's chapters; listing rows don't carry them
@Serializable
class MangaChaptersDto(
    val capitulos: List<ChapterDto>,
)

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
class FilterDataDto(
    val genres: List<GenreDto>,
    val scans: List<ScanFilterDto>,
)

@Serializable
class ScanFilterDto(
    val id: Int,
    val nombre: String,
)

@Serializable
class ScanDto(
    val nombre: String,
)

@Serializable
class ChapterDto(
    val numero: Double,
    private val titulo: String? = null,
    val temporada: Int? = null,
    val estado: String? = null,
    @SerialName("fecha_publicacion") val fechaPublicacion: String? = null,
    @SerialName("date_created") val dateCreated: String? = null,
) {
    fun toSChapter(mangaUrl: String, scanlator: String?, dateUpload: Long) = SChapter.create().apply {
        val num = numero.toString().removeSuffix(".0")
        url = "$mangaUrl/$num"
        name = titulo?.takeIf(String::isNotBlank) ?: "Capítulo $num"
        chapter_number = numero.toFloat()
        date_upload = dateUpload
        this.scanlator = scanlator
    }
}

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
    val retryAfterMs: Long? = null,
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
