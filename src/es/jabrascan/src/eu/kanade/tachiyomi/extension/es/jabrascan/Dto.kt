package eu.kanade.tachiyomi.extension.es.jabrascan

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.string
import keiyoushi.utils.stringOrNull
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import java.time.format.DateTimeFormatter

@Serializable
class WorkDto(
    @SerialName("obra_id") val id: String,
    private val visible: String? = null,
    @SerialName("nombreobra") private val name: String,
    @SerialName("sinopsis") private val synopsis: JsonElement? = null,
    @SerialName("tipoobra") private val type: String? = null,
    @SerialName("estado") private val status: String? = null,
    @SerialName("autor") private val author: String? = null,
    @SerialName("categoria") private val categories: List<String>? = null,
    @SerialName("nombresAlternativos") private val altNames: List<String> = emptyList(),
    @SerialName("imagen") private val images: List<String> = emptyList(),
    @SerialName("UltimoCapFecha") val lastChapterDate: String? = null,
) {
    val isVisible get() = visible == "si"

    fun matches(query: String) = (altNames + name + listOfNotNull(author)).any { it.contains(query, ignoreCase = true) }

    fun toSManga() = SManga.create().apply {
        url = id
        title = name
        thumbnail_url = images.firstOrNull()?.let { "$IMAGE_URL/${it.substringBeforeLast('.')}-900w.webp" }
        author = this@WorkDto.author
        genre = categories.orEmpty().flatMap { it.split(",") }.map { it.trim() }.filter { it.isNotEmpty() }.joinToString().ifEmpty { null }
        description = MarkdownDescription.Builder()
            .description(synopsisText())
            .field("Tipo", type)
            .altTitles(altNames)
            .build()
        status = when (this@WorkDto.status?.lowercase()) {
            "activo" -> SManga.ONGOING
            "finalizado" -> SManga.COMPLETED
            "pausado" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }

    // The synopsis is a text, or a list whose last item is the text without the XML markup of the first
    private fun synopsisText() = ((synopsis as? JsonArray)?.lastOrNull()?.string ?: synopsis?.stringOrNull)
        ?.removePrefix("<![CDATA[")
        ?.substringBefore("]]>")
        ?.lines()
        ?.joinToString("\n") { it.trim() }
        ?.trim()
}

@Serializable
class ChapterDto(
    @SerialName("Fecha") private val date: String,
    @SerialName("NombreArchivo") private val file: String,
    @SerialName("numCapitulo") val number: String,
    @SerialName("nombreCapitulo") private val name: String,
) {
    val uploadDate get() = DATE_FORMAT.tryParseDate(date)

    val isPdf get() = file.endsWith(".pdf", ignoreCase = true)

    fun toSChapter(workId: String) = SChapter.create().apply {
        url = "$workId/$number/$file"
        name = "$number: ${this@ChapterDto.name}"
        number.toFloatOrNull()?.let { chapter_number = it }
        date_upload = uploadDate
    }
}

private const val IMAGE_URL = "https://img.jabrascan.net"

private val DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy")
