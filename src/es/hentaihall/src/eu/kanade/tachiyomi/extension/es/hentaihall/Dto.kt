package eu.kanade.tachiyomi.extension.es.hentaihall

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Serializable
class PageDto(
    val data: List<MangaDto> = emptyList(),
    val next: Boolean = false,
)

@Serializable
class MangaDto(
    @SerialName("_id") private val id: String,
    private val nombre: String,
    private val imagen: String,
) {
    fun toSManga() = SManga.create().apply {
        title = nombre
        thumbnail_url = imagen
        url = id
    }
}

@Serializable
class DetailsDto(
    @SerialName("_id") private val id: String,
    private val nombre: String,
    private val imagen: String,
    private val tags: List<String> = emptyList(),
    private val autores: List<String> = emptyList(),
    private val tipo: String? = null,
    private val creacion: String? = null,
    @SerialName("name_grupo") private val nameGrupo: String? = null,
    private val lenguaje: String? = null,
) {
    fun toSManga() = SManga.create().apply {
        title = nombre
        thumbnail_url = imagen
        author = autores.joinToString(", ")
        artist = author
        genre = tags.joinToString(", ")
        status = SManga.COMPLETED
        initialized = true
        description = buildString {
            tipo?.let { append("Tipo: ", it.replaceFirstChar { char -> char.uppercase() }, "\n") }
            lenguaje?.let {
                val langStr = if (it == "esp") "Español" else it
                append("Lenguaje: ", langStr, "\n")
            }
            nameGrupo?.let { append("Grupo: ", it, "\n") }
        }
    }

    fun toSChapter() = SChapter.create().apply {
        name = "Chapter"
        url = id
        date_upload = dateFormat.tryParse(creacion)
    }

    companion object {
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }
}

@Serializable
class ChapterDto(
    val chapter: List<String> = emptyList(),
)
