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
data class GreenShitLoginRequestDto(
    val login: String,
    val senha: String,
    @SerialName("tipo_usuario") val tipoUsuario: String,
)

@Serializable
data class GreenShitLoginResponseDto(
    @JsonNames("access_token", "token") val accessToken: String,
    @SerialName("expires_in") val expiresIn: Long = 3600,
)

@Serializable
data class GreenShitListDto<T>(
    val obras: T,
    @JsonNames("currentPage", "pagina_atual", "pagina") val currentPage: Int? = null,
    @JsonNames("totalPages", "paginas", "totalPaginas") val totalPages: Int = 0,
) {
    val hasNextPage: Boolean get() = totalPages > (currentPage ?: 0)
}

@Serializable
data class GreenShitTagDto(
    @SerialName("tag_nome") val name: String = "",
)

@Serializable
data class GreenShitStatusDto(
    @SerialName("stt_nome") val name: String,
)

@Serializable
data class GreenShitChapterSimpleDto(
    @SerialName("cap_id") val id: Int,
    @SerialName("cap_nome") val name: String,
    @SerialName("cap_numero") val number: Float? = null,
    @SerialName("cap_criado_em") val createdAt: String? = null,
)

@Serializable
data class GreenShitMangaDto(
    @SerialName("obr_id") val id: Int,
    @SerialName("obr_nome") val name: String,
    @SerialName("obr_descricao") val description: String? = null,
    @SerialName("obr_imagem") val image: String? = null,
    val tags: List<GreenShitTagDto> = emptyList(),
    val status: GreenShitStatusDto? = null,
    @SerialName("scan_id") val scanId: Int = 0,
    @SerialName("capitulos") val chapters: List<GreenShitChapterSimpleDto> = emptyList(),
)

@Serializable
data class GreenShitPageSrcDto(
    val src: String,
    val mime: String? = null,
)

@Serializable
data class GreenShitChapterDetailDto(
    @SerialName("cap_id") val id: Int,
    @SerialName("cap_nome") val name: String,
    @SerialName("cap_numero") val number: Float? = null,
    @SerialName("cap_paginas") val pages: List<GreenShitPageSrcDto> = emptyList(),
    @SerialName("obra") val manga: GreenShitMangaDto? = null,
)

private fun isWpLikePath(src: String): Boolean = src.startsWith("uploads/") ||
    src.startsWith("wp-content/") ||
    src.startsWith("manga_") ||
    src.startsWith("WP-manga")

private fun normalizeSlashes(url: String): String = url.replace(Regex("(?<!:)/{2,}"), "/")

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
    val safePath = if (path.isNotBlank()) path.replace("//", "/").trimStart('/').trimEnd('/') else ""
    val safeSrc = src.replace("//", "/").trimStart('/').trimEnd('/')
    return normalizeSlashes("$base/$safePath/$safeSrc$query")
}

fun GreenShitMangaDto.toSManga(cdnUrl: String, isDetails: Boolean = false): SManga {
    val sManga = SManga.create().apply {
        title = name
        thumbnail_url = buildImageUrl(path = "/scans/$scanId/obras/$id/", src = image ?: "", width = 300, base = cdnUrl)
        initialized = isDetails
        url = "/obra/$id"
        genre = tags.joinToString { it.name }
    }

    description?.let { sManga.description = Jsoup.parseBodyFragment(it).text() }

    status?.let {
        sManga.status = when (it.name.lowercase()) {
            "Em Andamento", "ATIVO" -> SManga.ONGOING
            "Concluído" -> SManga.COMPLETED
            "Hiato" -> SManga.ON_HIATUS
            "Cancelado" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }
    return sManga
}

fun GreenShitChapterSimpleDto.toSChapter(): SChapter = SChapter.create().apply {
    name = this@toSChapter.name
    chapter_number = number ?: 0f
    url = "/capitulo/$id"
    date_upload = dateFormat.tryParse(createdAt)
}

fun GreenShitChapterDetailDto.toPageList(cdnUrl: String): List<Page> {
    val obraId = manga?.id ?: 0
    val scanId = manga?.scanId ?: 0
    val chapterNumber = number.toString()?.removeSuffix(".0") ?: "0"
    return pages.mapIndexed { idx, p ->
        val imageUrl = buildImageUrl(path = "/scans/$scanId/obras/$obraId/capitulos/$chapterNumber/", src = p.src, mime = p.mime, width = null, base = cdnUrl)
        Page(idx, imageUrl = imageUrl)
    }
}

private val dateFormat by lazy {
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
}
