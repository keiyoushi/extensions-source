package eu.kanade.tachiyomi.extension.pt.erosect

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.net.URLEncoder
import java.text.SimpleDateFormat

private val timezoneColonRegex = Regex("""([+-]\d{2}):(\d{2})$""")

@Serializable
class PaginatedResponse(
    private val pagination: PaginationDto,
    private val obras: List<ObraDto> = emptyList(),
) {
    val hasNextPage: Boolean
        get() = pagination.hasNextPage()

    fun toSMangaList(): List<SManga> = obras.map { it.toSManga() }
}

@Serializable
class PopularResponse(
    private val obras: List<PopularObraDto> = emptyList(),
) {
    fun toSMangaList(): List<SManga> = obras.map { it.toSManga() }
}

@Serializable
class PaginationDto(
    private val hasNextPage: Boolean,
) {
    fun hasNextPage(): Boolean = hasNextPage
}

@Serializable
class PopularObraDto(
    private val id: Int,
    private val title: String,
    private val coverImage: String? = null,
) {
    fun toSManga() = SManga.create().apply {
        this.title = this@PopularObraDto.title
        url = "/obra/$id"
        thumbnail_url = coverImage?.toEroSectThumbnailUrl()
    }
}

@Serializable
class ObraDto(
    private val id: Int,
    private val nome: String,
    private val descricao: String? = null,
    private val imagem: String? = null,
    @SerialName("status_nome") private val statusNome: String? = null,
    private val tags: List<TagDto> = emptyList(),
) {
    fun toSManga(initialized: Boolean = false) = SManga.create().apply {
        title = nome
        url = "/obra/$id"
        thumbnail_url = imagem?.toEroSectThumbnailUrl()
        description = descricao
        genre = tags.joinToString(", ") { it.toGenre() }
        status = when {
            statusNome == "Em Andamento" -> SManga.ONGOING
            statusNome == "Cancelada" -> SManga.CANCELLED
            statusNome == "Completo" || statusNome?.contains("Conclu", ignoreCase = true) == true -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        this.initialized = initialized
    }
}

@Serializable
class TagDto(
    private val nome: String,
) {
    fun toGenre(): String = nome
}

@Serializable
class ObraDetailResponse(
    private val obra: ObraDto,
) {
    fun toSManga(): SManga = obra.toSManga(initialized = true)
}

@Serializable
class CapitulosResponse(
    private val capitulos: List<CapituloDto> = emptyList(),
) {
    fun toSChapterList(dateFormat: SimpleDateFormat): List<SChapter> = capitulos
        .map { it.toSChapter(dateFormat) }
        .sortedWith(compareByDescending<SChapter> { it.chapter_number }.thenByDescending { it.date_upload })
}

@Serializable
class CapituloDto(
    @SerialName("obra_id") private val obraId: Int,
    private val numero: String,
    private val nome: String? = null,
    @SerialName("criado_em") private val criadoEm: String,
) {
    fun toSChapter(dateFormat: SimpleDateFormat) = SChapter.create().apply {
        name = nome ?: "Capitulo ${numero.toFloatOrNull()?.toString()?.removeSuffix(".0") ?: numero}"
        url = "/obra/$obraId/capitulo/$numero"
        chapter_number = numero.toFloatOrNull() ?: -1f
        date_upload = dateFormat.tryParse(criadoEm.normalizeTimezoneOffset())
    }
}

@Serializable
class PageListResponse(
    private val capitulo: PageChapterDto,
) {
    fun toPageList(baseUrl: String): List<Page> = capitulo.toPageList(baseUrl)
}

@Serializable
class PageChapterDto(
    @SerialName("obra_id") private val obraId: Int,
    private val numero: String,
    private val paginas: List<PageDto>,
) {
    fun toPageList(baseUrl: String): List<Page> {
        val chapterUrl = "$baseUrl/obra/$obraId/capitulo/$numero"
        return paginas
            .sorted()
            .mapIndexed { index, page -> page.toPage(index, baseUrl, chapterUrl) }
    }
}

@Serializable
class PageDto(
    @SerialName("numero") private val number: Int,
    private val url: String,
) : Comparable<PageDto> {
    override fun compareTo(other: PageDto): Int = number.compareTo(other.number)

    fun toPage(index: Int, baseUrl: String, chapterUrl: String): Page = Page(
        index,
        url = chapterUrl,
        imageUrl = url.toAbsoluteUrl(baseUrl),
    )
}

@Serializable
class LoginRequest(
    private val email: String,
    @SerialName("senha") private val password: String,
)

@Serializable
class LoginResponse(
    @SerialName("sucesso") private val success: Boolean,
    private val token: String? = null,
) {
    fun tokenOrNull(): String? = token?.takeIf { success && it.isNotBlank() }
}

private fun String.toEroSectThumbnailUrl(): String {
    val imgUrl = if (startsWith("http")) this else "https://cdn.erosect.xyz/$this"
    return "https://erosect.xyz/_next/image?url=${URLEncoder.encode(imgUrl, "UTF-8")}&w=3840&q=75"
}

private fun String.toAbsoluteUrl(baseUrl: String): String = when {
    startsWith("http") -> this
    startsWith("//") -> "https:$this"
    startsWith("/") -> "$baseUrl$this"
    else -> "$baseUrl/$this"
}

private fun String.normalizeTimezoneOffset(): String = replace(timezoneColonRegex, "$1$2")
