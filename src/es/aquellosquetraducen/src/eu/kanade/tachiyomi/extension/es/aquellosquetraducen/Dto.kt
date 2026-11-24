package eu.kanade.tachiyomi.extension.es.aquellosquetraducen

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

private val driveIdRegex = Regex("""[-\w]{25,}""")

// Original file from Drive's image host; without "=s0" it caps the long side at 1600 px
fun driveImageUrl(id: String) = "https://lh3.googleusercontent.com/d/$id=s0"

@Serializable
class WorksDto(
    val documents: List<WorkDto> = emptyList(),
    val nextPageToken: String? = null,
)

@Serializable
class WorkDto(
    private val name: String,
    private val fields: WorkFieldsDto,
) {
    val id get() = name.substringAfterLast('/')
    val title get() = fields.titulo.stringValue
    val type get() = fields.tipo?.stringValue
    val status get() = fields.estado?.stringValue
    val genres get() = fields.generos?.arrayValue?.values.orEmpty().map { it.stringValue }
    val likes get() = fields.likes?.integerValue?.toLong() ?: 0L
    val folderId get() = fields.driveFolderId?.stringValue
    val isNovel get() = type == "Novela"

    fun toSManga() = SManga.create().apply {
        url = "/obra.html?id=$id"
        title = this@WorkDto.title
        // The site keeps either a Drive file ID or a Drive link
        thumbnail_url = fields.portadaUrl?.stringValue?.let { cover ->
            driveIdRegex.find(cover)?.let { driveImageUrl(it.value) } ?: cover
        }
        description = MarkdownDescription.Builder()
            .info(
                fields.likes?.let { "👍 ${it.integerValue}" },
                fields.dislikes?.let { "👎 ${it.integerValue}" },
                fields.visitas?.let { "${it.integerValue} visitas" },
            )
            .description(fields.sinopsis?.stringValue)
            .build()
        genre = (listOfNotNull(type) + genres).joinToString().ifEmpty { null }
        status = when (this@WorkDto.status) {
            "emision" -> SManga.ONGOING
            "pausa" -> SManga.ON_HIATUS
            "finalizado" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        initialized = true
    }
}

@Serializable
class WorkFieldsDto(
    val titulo: StringValueDto,
    val sinopsis: StringValueDto? = null,
    @SerialName("portadaURL") val portadaUrl: StringValueDto? = null,
    val tipo: StringValueDto? = null,
    val estado: StringValueDto? = null,
    val generos: ArrayValueDto? = null,
    val likes: IntegerValueDto? = null,
    val dislikes: IntegerValueDto? = null,
    val visitas: IntegerValueDto? = null,
    val driveFolderId: StringValueDto? = null,
)

@Serializable
class StringValueDto(val stringValue: String)

@Serializable
class IntegerValueDto(val integerValue: String)

@Serializable
class ArrayValueDto(val arrayValue: ValuesDto)

@Serializable
class ValuesDto(val values: List<StringValueDto> = emptyList())

@Serializable
class FilesDto(val files: List<FileDto>)

@Serializable
class FileDto(
    val id: String,
    val name: String,
    private val createdTime: String? = null,
) {
    fun toSChapter(workId: String) = SChapter.create().apply {
        url = "/lector.html?obra=$workId&cap=$id"
        name = "Capítulo ${this@FileDto.name}"
        date_upload = Instant.tryParse(createdTime)
    }
}
