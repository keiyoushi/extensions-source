package eu.kanade.tachiyomi.extension.es.ritto

import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.Serializable
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField

// The site writes its dates like "5 nov 2026" or "3 sept 2026"
private val dateFormat: DateTimeFormatter = DateTimeFormatterBuilder()
    .appendPattern("d ")
    .appendText(
        ChronoField.MONTH_OF_YEAR,
        listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sept", "oct", "nov", "dic")
            .mapIndexed { i, month -> i + 1L to month }.toMap(),
    )
    .appendPattern(" yyyy")
    .toFormatter()

private val zone = ZoneId.of("America/Santiago")

@Serializable
class PaginationDto(
    private val paginaActual: Int,
    private val totalPaginas: Int,
) {
    fun hasNextPage() = paginaActual < totalPaginas
}

@Serializable
class ChapterDto(
    private val nombre: String,
    private val tituloExtra: String? = null,
    private val href: String,
    private val fechaLabel: String? = null,
    private val scanLabel: String? = null,
    private val capituloEsImagen: Boolean,
    private val numero: Float? = null,
    private val versiones: List<ChapterDto> = emptyList(),
) {
    // Other scans' versions of a chapter come in versiones, without their own number
    fun toSChapters(): List<SChapter> = (listOf(this) + versiones)
        .filter { it.capituloEsImagen }
        .map { it.toSChapter(numero) }

    private fun toSChapter(number: Float?) = SChapter.create().apply {
        url = href
        name = listOfNotNull(nombre, tituloExtra?.takeIf { it.isNotBlank() }).joinToString(" - ")
        scanlator = scanLabel
        number?.let { chapter_number = it }
        date_upload = dateFormat.tryParseDate(fechaLabel, zone)
    }
}
