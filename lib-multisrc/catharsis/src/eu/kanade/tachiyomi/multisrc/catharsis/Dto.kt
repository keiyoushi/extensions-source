package eu.kanade.tachiyomi.multisrc.catharsis

import eu.kanade.tachiyomi.source.model.SChapter
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
    val id: Int,
    val slug: String,
    private val nombre: String,
    @SerialName("portada_url") private val portadaUrl: String?,
    private val descripcion: String?,
    private val estado: String?,
    private val tipo: String?,
    private val nsfw: Boolean = false,
    @SerialName("nombre_alt_1") private val nombreAlt1: String?,
    @SerialName("nombre_alt_2") private val nombreAlt2: String?,
    @SerialName("fk_generos") private val generos: List<MangaGenreDto> = emptyList(),
    @SerialName("fk_scans") private val scan: ScanDto?,
) {
    val scanlator get() = scan?.nombre

    fun toSManga(mangaKey: String, baseUrl: String) = SManga.create().apply {
        url = mangaKey
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
class ScanDto(
    val nombre: String,
)

@Serializable
class ChapterDto(
    val numero: Double,
    private val titulo: String?,
    val temporada: Int?,
    val estado: String?,
    @SerialName("date_created") val dateCreated: String?,
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

// What the page's reader posted: a challenge, an error status, or what it read
@Serializable
internal class PageRead(
    val challenge: Boolean = false,
    val status: Int?,
    val obstacle: Obstacle?,
    val result: JsonObject?,
)

// Something the site put over the page, and where its close button is
@Serializable
internal class Obstacle(val x: Float, val y: Float, val scale: Float)

/** A spot to tap in CSS pixels, and whether the element it aims at is what a tap there would hit. */
@Serializable
class Point(
    val x: Float,
    val y: Float,
    val clear: Boolean = true,
    // Where a swipe towards it starts, when it scrolls inside a box of its own
    val fromX: Float? = null,
    val fromY: Float? = null,
)

// What the reader script reads from the site's chapter reader
@Serializable
internal class ReaderPage(
    val scale: Float,
    val width: Float,
    val height: Float,
    val gate: Point?,
    val check: Point?,
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
