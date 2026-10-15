package eu.kanade.tachiyomi.extension.es.lectormangalat

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Instant

@Serializable
class SeriesListDto(
    val data: List<SeriesDto>,
    private val meta: MetaDto? = null,
) {
    // meta is omitted when all results fit on one page
    val hasNextPage get() = meta != null && meta.currentPage < meta.lastPage
}

@Serializable
class MetaDto(
    @SerialName("current_page") val currentPage: Int,
    @SerialName("last_page") val lastPage: Int,
)

@Serializable
class SeriesDetailsDto(val data: SeriesDto)

@Serializable
class SeriesDto(
    private val id: Int,
    val slug: String,
    private val titulo: String,
    @SerialName("titulo_alternativo") private val tituloAlternativo: String? = null,
    private val sinopsis: String? = null,
    private val portada: String? = null,
    private val tipo: String? = null,
    private val estado: String? = null,
    private val generos: List<String> = emptyList(),
    private val grupo: GroupDto? = null,
    val capitulos: List<ChapterDto> = emptyList(),
) {
    fun toSManga() = SManga.create().apply {
        url = id.toString()
        title = titulo
        thumbnail_url = portada
        memo = buildJsonObject { put("slug", slug) }
    }

    fun toSMangaDetails() = toSManga().apply {
        genre = (listOfNotNull(tipo) + generos).joinToString()
        description = buildString {
            sinopsis?.let { append(it) }
            tituloAlternativo?.takeIf(String::isNotBlank)?.let {
                if (isNotEmpty()) append("\n\n")
                append("Nombres alternativos: ", it)
            }
            grupo?.let {
                if (isNotEmpty()) append("\n\n")
                append("Grupo: ", it.nombre)
            }
        }
        status = when (estado?.lowercase()) {
            "en emisión" -> SManga.ONGOING
            "finalizado" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }
}

@Serializable
class GroupDto(val nombre: String)

@Serializable
class ChapterDto(
    private val id: Int,
    private val numero: JsonPrimitive,
    private val titulo: String? = null,
    @SerialName("publicado_en") private val publicadoEn: String? = null,
) {
    fun toSChapter(seriesSlug: String) = SChapter.create().apply {
        url = id.toString()
        name = buildString {
            append("Capítulo ", numero.content)
            titulo?.takeIf(String::isNotBlank)?.let { append(" - ", it) }
        }
        chapter_number = numero.content.toFloatOrNull() ?: -1f
        date_upload = Instant.tryParse(publicadoEn)
        memo = buildJsonObject {
            put("series", seriesSlug)
            put("number", numero.content)
        }
    }
}

@Serializable
class ChapterPagesDto(val data: ChapterPagesDataDto)

@Serializable
class ChapterPagesDataDto(val paginas: List<String>)
