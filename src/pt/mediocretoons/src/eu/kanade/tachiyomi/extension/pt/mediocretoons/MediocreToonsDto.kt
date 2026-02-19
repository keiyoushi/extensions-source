package eu.kanade.tachiyomi.extension.pt.mediocretoons

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
data class MediocrePaginationDto(
    @JsonNames("currentPage", "pagina_atual") val currentPage: Int? = null,
    @JsonNames("totalPages", "paginas") val totalPages: Int = 0,
    @JsonNames("totalItems", "total") val totalItems: Int = 0,
    @JsonNames("itemsPerPage", "itens_por_pagina") val itemsPerPage: Int = 0,
) {
    val hasNextPage: Boolean get() = totalPages > (currentPage ?: 0)
    val hasPreviousPage: Boolean get() = 1 < (currentPage ?: 0)
}

@Serializable
data class MediocreListDto<T>(
    val data: T,
    val pagination: MediocrePaginationDto? = null,
)

@Serializable
data class MediocreTagDto(
    val id: Int = 0,
    @SerialName("nome") val name: String = "",
)

@Serializable
data class MediocreFormatDto(
    val id: Int = 0,
    @SerialName("nome") val name: String = "",
)

@Serializable
data class MediocreRankingDto(
    @SerialName("id") val id: Int = 0,
    @SerialName("nome") val name: String = "",
    @SerialName("imagem") val image: String? = null,
    @SerialName("views_hoje") val viewsToday: Int = 0,
    @SerialName("view_semana") val viewsWeek: Int = 0,
    @SerialName("view_geral") val viewsTotal: Int = 0,
)

@Serializable
data class MediocreStatusDto(
    val id: Int = 0,
    @SerialName("nome") val name: String = "",
)

@Serializable
data class MediocreChapterSimpleDto(
    val id: Int = 0,
    @SerialName("nome") val name: String = "",
    @SerialName("numero") val number: Float? = null,
    @SerialName("imagem") val image: String? = null,
    @SerialName("lancado_em") val releasedAt: String? = null,
    @SerialName("criado_em") val createdAt: String? = null,
    @SerialName("descricao") val description: String? = null,
    @SerialName("tem_paginas") val hasPages: Boolean = false,
    val totallinks: Int = 0,
    @SerialName("lido") val read: Boolean = false,
    val views: Int = 0,
)

@Serializable
data class MediocreMangaDto(
    val id: Int = 0,
    val slug: String? = null,
    @SerialName("nome") val name: String = "",
    @SerialName("descricao") val description: String? = null,
    @SerialName("imagem") val image: String? = null,
    @SerialName("formato") val format: MediocreFormatDto? = null,
    val tags: List<MediocreTagDto> = emptyList(),
    val status: MediocreStatusDto? = null,
    @SerialName("total_capitulos") val totalChapters: Int = 0,
    @SerialName("capitulos") val chapters: List<MediocreChapterSimpleDto> = emptyList(),
)

@Serializable
data class MediocrePageSrcDto(
    val src: String = "",
)

@Serializable
data class MediocreChapterDetailDto(
    val id: Int = 0,
    @SerialName("nome") val name: String = "",
    @SerialName("numero") val number: Float? = null,
    @SerialName("imagem") val image: String? = null,
    @SerialName("paginas") val pages: List<MediocrePageSrcDto> = emptyList(),
    @SerialName("lancado_em") val releasedAt: String? = null,
    @SerialName("criado_em") val createdAt: String? = null,
    @SerialName("obra") val manga: MediocreMangaDto? = null,
)

fun MediocreMangaDto.toSManga(isDetails: Boolean = false): SManga {
    val sManga = SManga.create().apply {
        title = name
        thumbnail_url = image?.let {
            when {
                it.startsWith("http") -> it
                else -> "${MediocreToons.CDN_URL}/obras/${this@toSManga.id}/$it?v=3"
            }
        }
        initialized = isDetails
        url = "/obra/$id"
        genre = tags.joinToString { it.name }
    }

    description?.let { sManga.description = Jsoup.parseBodyFragment(it).text() }

    status?.let {
        sManga.status = when (it.name.lowercase()) {
            "em andamento", "ativo" -> SManga.ONGOING
            "completo", "concluído" -> SManga.COMPLETED
            "hiato" -> SManga.ON_HIATUS
            "cancelada" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }
    return sManga
}

fun MediocreChapterSimpleDto.toSChapter(): SChapter = SChapter.create().apply {
    name = this@toSChapter.name
    chapter_number = number ?: 0f
    url = "/capitulo/$id"
    date_upload = dateFormat.tryParse(createdAt)
}

fun MediocreChapterDetailDto.toPageList(): List<Page> {
    val obraId = manga?.id ?: 0
    val chapterNumber = number?.toString()?.removeSuffix(".0") ?: name
    return pages.mapIndexed { idx, p ->
        val imageUrl = "${MediocreToons.CDN_URL}/obras/$obraId/capitulos/$chapterNumber/${p.src}"
        Page(idx, imageUrl = imageUrl)
    }
}

private val dateFormat by lazy {
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
}
