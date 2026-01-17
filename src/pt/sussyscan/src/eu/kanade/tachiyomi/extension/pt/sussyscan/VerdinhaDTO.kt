package eu.kanade.tachiyomi.extension.pt.sussyscan

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ========================= Manga List =========================

@Serializable
data class MangaListDto(
    val obras: List<MangaDto> = emptyList(),
    val pagina: Int = 1,
    val totalPaginas: Int = 1,
)

@Serializable
data class MangaDto(
    @SerialName("obr_id") val id: Int,
    @SerialName("obr_nome") val name: String,
    @SerialName("obr_slug") val slug: String? = null,
    @SerialName("obr_imagem") val image: String? = null,
    @SerialName("obr_vip") val vip: Boolean = false,
    @SerialName("scan_id") val scanId: Int = 1,
) {
    fun toSManga(cdnUrl: String) = SManga.create().apply {
        title = name
        url = "/obra/${slug ?: id}"
        thumbnail_url = image?.let {
            if (it.startsWith("wp-content/")) {
                "$cdnUrl/$it"
            } else {
                "$cdnUrl/scans/$scanId/obras/$id/$it"
            }
        }
    }
}

// ========================= Manga Details =========================

@Serializable
data class MangaDetailsDto(
    @SerialName("obr_id") val id: Int,
    @SerialName("obr_nome") val name: String,
    @SerialName("obr_slug") val slug: String? = null,
    @SerialName("obr_imagem") val image: String? = null,
    @SerialName("obr_descricao") val description: String? = null,
    @SerialName("scan_id") val scanId: Int = 1,
    val genero: GeneroDto? = null,
    val formato: FormatoDto? = null,
    val status: StatusDto? = null,
    val tags: List<TagDto> = emptyList(),
    val capitulos: List<ChapterDto> = emptyList(),
) {
    fun toSManga(cdnUrl: String) = SManga.create().apply {
        title = name
        url = "/obra/${slug ?: id}"
        thumbnail_url = image?.let {
            if (it.startsWith("wp-content/")) {
                "$cdnUrl/$it"
            } else {
                "$cdnUrl/scans/$scanId/obras/$id/$it"
            }
        }
        description = this@MangaDetailsDto.description
        genre = buildList {
            genero?.name?.let { add(it) }
            formato?.name?.let { add(it) }
            addAll(tags.map { it.name })
        }.joinToString()
        status = when (this@MangaDetailsDto.status?.id) {
            1 -> SManga.ONGOING
            2 -> SManga.COMPLETED
            3 -> SManga.ON_HIATUS
            4 -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        initialized = true
    }
}

@Serializable
data class GeneroDto(
    @SerialName("gen_id") val id: Int,
    @SerialName("gen_nome") val name: String,
)

@Serializable
data class FormatoDto(
    @SerialName("formt_id") val id: Int,
    @SerialName("formt_nome") val name: String,
)

@Serializable
data class StatusDto(
    @SerialName("stt_id") val id: Int,
    @SerialName("stt_nome") val name: String,
)

@Serializable
data class TagDto(
    @SerialName("tag_id") val id: Int,
    @SerialName("tag_nome") val name: String,
)

// ========================= Chapters =========================

@Serializable
data class ChapterDto(
    @SerialName("cap_id") val id: Int,
    @SerialName("cap_nome") val name: String,
    @SerialName("cap_numero") val number: Float,
    @SerialName("cap_liberar_em") val releaseDate: String? = null,
    @SerialName("cap_criado_em") val createdDate: String? = null,
)

// ========================= Pages =========================

@Serializable
data class PagesDto(
    @SerialName("cap_id") val id: Int,
    @SerialName("cap_numero") val chapterNumber: Int,
    @SerialName("obr_id") val obraId: Int,
    @SerialName("cap_paginas") val pages: List<PageDto> = emptyList(),
    val obra: PageObraDto? = null,
)

@Serializable
data class PageObraDto(
    @SerialName("scan_id") val scanId: Int = 1,
)

@Serializable
data class PageDto(
    val src: String? = null,
    val path: String? = null,
)

// ========================= Auth =========================

@Serializable
data class AuthRequestDto(
    val login: String,
    val senha: String,
    @SerialName("tipo_usuario") val userType: String,
)

@Serializable
data class AuthResponseDto(
    @SerialName("access_token") val accessToken: String,
)
