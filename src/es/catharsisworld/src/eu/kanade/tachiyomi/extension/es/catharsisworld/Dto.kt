package eu.kanade.tachiyomi.extension.es.catharsisworld

import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl

@Serializable
class MangaListDto(
    val data: List<MangaDto>,
    @SerialName("total_pages") val totalPages: Int,
)

@Serializable
class MangaDto(
    private val id: Int,
    private val nombre: String,
    @SerialName("portada_url") private val portadaUrl: String?,
    private val descripcion: String?,
    private val estado: String?,
    private val tipo: String?,
    private val nsfw: Boolean = false,
    @SerialName("nombre_alt_1") private val nombreAlt1: String?,
    @SerialName("nombre_alt_2") private val nombreAlt2: String?,
    @SerialName("fk_generos") private val generos: List<MangaGenreDto> = emptyList(),
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        url = id.toString()
        title = nombre
        // A bare file id, or its path under /assets
        thumbnail_url = portadaUrl?.let { baseUrl.toHttpUrl().resolve(if (it.startsWith("/")) it else "/assets/$it")?.toString() }
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

@Serializable
class MangaGenreDto(
    @SerialName("generos_id") val genre: GenreDto,
)

@Serializable
class GenreDto(
    val nombre: String,
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
    val token: String?,
)

@Serializable
class ReaderPassRequest(
    private val token: String,
    private val sitekey: String,
)

@Serializable
internal class PageRead(
    val challenge: Boolean = false,
    val status: Int?,
    val obstacle: Obstacle?,
    val result: JsonObject?,
)

@Serializable
internal class Obstacle(val x: Float, val y: Float, val scale: Float)

/** A point in CSS pixels, and whether its element is the one found at that point. */
@Serializable
class Point(
    val x: Float,
    val y: Float,
    val clear: Boolean = true,
    val fromX: Float?,
    val fromY: Float?,
)

// What the reader script reads from the site's chapter reader
@Serializable
internal class ReaderPage(
    val scale: Float,
    val width: Float,
    val height: Float,
    val gate: Point?,
    val check: Boolean = false,
    val turnstile: Boolean = false,
    val textOnly: Boolean = false,
    val pages: List<ReaderItem> = emptyList(),
    val grip: Point?,
    val text: String?,
    val image: PageImage?,
)

@Serializable
internal class ReaderItem(val index: Int, val top: Float, val height: Float, val ready: Boolean, val retry: Point?)

@Serializable
internal class PageImage(val index: Int, val data: String)

@Serializable
internal class SeriesPage(
    val scale: Float,
    val id: String?,
    val height: Float,
    val width: Float,
    val tap: Point?,
    val title: String?,
    val altTitle: String?,
    val badges: List<String> = emptyList(),
    val description: String?,
    val cover: String?,
    val scans: List<String> = emptyList(),
    val author: String?,
    val rows: List<Row> = emptyList(),
    val more: Point?,
    val count: Int = -1,
    val rating: Float?,
    val votes: Int?,
)

@Serializable
internal class ListingPage(
    val scale: Float,
    val width: Float,
    val height: Float,
    val tap: Point?,
    val list: MangaListDto?,
    val ids: Map<String, String?> = emptyMap(),
)

@Serializable
internal class GenrePanel(
    val scale: Float,
    val width: Float,
    val height: Float,
    val tap: Point?,
    val names: List<String>?,
    val ids: Map<String, String> = emptyMap(),
)

// A genre of the filter panel, with its id when the site's cached lists showed it
@Serializable
internal class GenreOption(val name: String, val id: String?)

@Serializable
internal class Row(val href: String, val heading: String, val title: String?, val age: String?)
