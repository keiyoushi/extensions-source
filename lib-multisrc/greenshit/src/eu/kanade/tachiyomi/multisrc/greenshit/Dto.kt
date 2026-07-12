package eu.kanade.tachiyomi.multisrc.greenshit

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Serializable
class GreenShitLoginRequestDto(
    private val login: String,
    private val senha: String,
    @SerialName("tipo_usuario") private val tipoUsuario: String,
)

@Serializable
class GreenShitLoginResponseDto(
    @JsonNames("access_token", "token") val accessToken: String,
    @SerialName("expires_in") val expiresIn: Long = 3600,
)

@Serializable
class GreenShitListDto<T>(
    val obras: T,
    @JsonNames("currentPage", "pagina_atual", "pagina") private val currentPage: Int? = null,
    @JsonNames("totalPages", "paginas", "totalPaginas") private val totalPages: Int = 0,
) {
    val hasNextPage: Boolean get() = totalPages > (currentPage ?: 0)
}

@Serializable
class GreenShitTagDto(
    @SerialName("tag_nome") val name: String = "",
)

@Serializable
class GreenShitGenreTagDto(
    @SerialName("gen_nome") val name: String = "",
)

@Serializable
class GreenShitStatusDto(
    @SerialName("stt_nome") val name: String,
)

@Serializable
class GreenShitChapterSimpleDto(
    @SerialName("cap_id") private val id: Int,
    @SerialName("cap_nome") private val name: String,
    @SerialName("cap_numero") private val number: Float? = null,
    @SerialName("cap_criado_em") private val createdAt: String? = null,
    @SerialName("cap_liberado") private val released: Boolean = true,
) {
    private val locked get() = if (!released) "🔒 " else ""

    fun toSChapter(): SChapter = SChapter.create().apply {
        name = locked + this@GreenShitChapterSimpleDto.name
        chapter_number = number ?: 0f
        url = "/capitulo/$id"
        date_upload = dateFormat.tryParse(createdAt)
    }
}

@Serializable
class GreenShitMangaDto(
    @SerialName("obr_id") val id: Int,
    @SerialName("obr_nome") private val name: String,
    @SerialName("obr_descricao") private val description: String? = null,
    @SerialName("obr_imagem") private val image: String? = null,
    private val genero: GreenShitGenreTagDto? = null,
    private val tags: List<GreenShitTagDto> = emptyList(),
    private val status: GreenShitStatusDto? = null,
    @SerialName("scan_id") val scanId: Int = 0,
    @SerialName("capitulos") val chapters: List<GreenShitChapterSimpleDto> = emptyList(),
) {
    fun toSManga(cdnUrl: String, isDetails: Boolean = false): SManga {
        val sManga = SManga.create().apply {
            title = name
            thumbnail_url = buildImageUrl(path = "/scans/$scanId/obras/$id/", src = image ?: "", width = 300, base = cdnUrl)
            initialized = isDetails
            url = "/obra/$id"
            genre = (listOfNotNull(genero?.name) + tags.map { it.name }).filter { it.isNotBlank() }.joinToString()
        }

        description?.let { sManga.description = Jsoup.parseBodyFragment(it).text() }

        status?.let {
            sManga.status = when (it.name.lowercase()) {
                "em andamento", "ativo" -> SManga.ONGOING
                "concluído" -> SManga.COMPLETED
                "hiato" -> SManga.ON_HIATUS
                "cancelado" -> SManga.CANCELLED
                else -> SManga.UNKNOWN
            }
        }
        return sManga
    }
}

@Serializable
class GreenShitPageSrcDto(
    val src: String,
    val mime: String? = null,
)

@Serializable
class GreenShitChapterDetailDto(
    @SerialName("cap_id") private val id: Int,
    @SerialName("cap_nome") private val name: String,
    @SerialName("cap_numero") private val number: Float? = null,
    @SerialName("cap_paginas") private val pages: List<GreenShitPageSrcDto> = emptyList(),
    @SerialName("obra") private val manga: GreenShitMangaDto? = null,
) {
    fun toPageList(cdnUrl: String): List<Page> {
        val obraId = manga?.id ?: 0
        val scanId = manga?.scanId ?: 0
        val chapterNumber = number?.toString()?.removeSuffix(".0") ?: "0"
        return pages.mapIndexed { idx, p ->
            val imageUrl = buildImageUrl(path = "/scans/$scanId/obras/$obraId/capitulos/$chapterNumber/", src = p.src, mime = p.mime, width = null, base = cdnUrl)
            Page(idx, imageUrl = imageUrl)
        }
    }
}

@Serializable
class GreenShitErrorDto(
    val message: String,
)

@Serializable
class GreenShitFiltersDto(
    private val generos: List<GreenShitGenreDto> = emptyList(),
    private val tags: List<GreenShitTagFilterDto> = emptyList(),
) {
    val genresList get() = generos.map { it.toPair() }
    val tagsList get() = tags.map { it.toPair() }
}

@Serializable
class GreenShitGenreDto(
    @SerialName("gen_nome") private val name: String,
    @JsonNames("gen_id") private val id: Int = 0,
) {
    fun toPair(): Pair<String, String> = Pair(name, id.toString())
}

@Serializable
class GreenShitTagFilterDto(
    @SerialName("tag_nome") private val name: String,
    @JsonNames("tag_id") private val id: Int = 0,
) {
    fun toPair(): Pair<String, String> = Pair(name, id.toString())
}

private fun isWpLikePath(src: String): Boolean = src.startsWith("uploads/") ||
    src.startsWith("wp-content/") ||
    src.startsWith("manga_") ||
    src.startsWith("WP-manga")

private val slashRegex = Regex("(?<!:)/{2,}")

private fun normalizeSlashes(url: String): String = url.replace(slashRegex, "/")

fun buildImageUrl(
    path: String = "",
    src: String = "",
    width: Int?,
    base: String,
    mime: String? = null,
): String {
    if (src.isBlank()) return ""
    if (src.startsWith("http")) return src
    if (isWpLikePath(src) || mime != null) {
        return when {
            src.startsWith("manga_") -> normalizeSlashes("$base/wp-content/uploads/WP-manga/data/$src")
            src.startsWith("WP-manga") -> normalizeSlashes("$base/wp-content/uploads/$src")
            src.startsWith("uploads/") -> normalizeSlashes("$base/wp-content/$src")
            src.startsWith("wp-content/") -> normalizeSlashes("$base/$src")
            else -> normalizeSlashes("$base/wp-content/uploads/WP-manga/data/${src.trimStart('/')}")
        }
    }
    val query = width?.let { "?width=$it" } ?: ""
    return normalizeSlashes("$base/$path/$src$query")
}

private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}
