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
data class GreenShitRankingDto(
    @SerialName("obr_id") val id: Int = 0,
    @SerialName("obr_nome") val name: String = "",
    @SerialName("obr_imagem") val image: String? = null,
    @SerialName("scan_id") val scanId: Int = 0,
)

@Serializable
data class GreenShitStatusDto(
    @SerialName("stt_nome") val name: String = "",
)

@Serializable
data class GreenShitChapterSimpleDto(
    @SerialName("cap_id") val id: Int = 0,
    @SerialName("cap_nome") val name: String = "",
    @SerialName("cap_numero") val number: Float? = null,
    @SerialName("cap_criado_em") val createdAt: String? = null,
)

@Serializable
data class GreenShitMangaDto(
    @SerialName("obr_id") val id: Int = 0,
    @SerialName("obr_nome") val name: String = "",
    @SerialName("obr_descricao") val description: String? = null,
    @SerialName("obr_imagem") val image: String? = null,
    val tags: List<GreenShitTagDto> = emptyList(),
    val status: GreenShitStatusDto? = null,
    @SerialName("scan_id") val scanId: Int = 0,
    @SerialName("capitulos") val chapters: List<GreenShitChapterSimpleDto> = emptyList(),
)

@Serializable
data class GreenShitPageSrcDto(
    val src: String = "",
)

@Serializable
data class GreenShitChapterDetailDto(
    @SerialName("cap_id") val id: Int = 0,
    @SerialName("cap_nome") val name: String = "",
    @SerialName("cap_numero") val number: Float? = null,
    @SerialName("cap_paginas") val pages: List<GreenShitPageSrcDto> = emptyList(),
    @SerialName("obra") val manga: GreenShitMangaDto? = null,
)

fun GreenShitMangaDto.toSManga(cdnUrl: String, isDetails: Boolean = false): SManga {
    val sManga = SManga.create().apply {
        title = name
        thumbnail_url = image?.let {
            when {
                it.startsWith("http") -> it
                else -> "$cdnUrl/scans/$scanId/obras/${this@toSManga.id}/$it?v=3"
            }
        }
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
    val capituloNome = number?.toInt()?.toString() ?: name
    return pages.mapIndexed { idx, p ->
        val imageUrl = "$cdnUrl/scans/$scanId/obras/$obraId/capitulos/$capituloNome/${p.src}"
        Page(idx, imageUrl = imageUrl)
    }
}

private val dateFormat by lazy {
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
}
