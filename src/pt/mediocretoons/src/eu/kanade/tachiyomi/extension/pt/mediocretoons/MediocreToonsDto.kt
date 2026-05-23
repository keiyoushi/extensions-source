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
    @JsonNames("data", "items") val data: T,
    val pagination: MediocrePaginationDto? = null,
)

@Serializable
data class MediocreTagDto(
    val id: Int = 0,
    @JsonNames("nome", "tag_nome") val name: String = "",
)

@Serializable
data class MediocreFormatDto(
    @JsonNames("id", "formt_id") val id: Int = 0,
    @JsonNames("nome", "formt_nome") val name: String = "",
)

@Serializable
data class MediocreStatusDto(
    val id: Int = 0,
    @JsonNames("nome", "status_nome") val name: String = "",
)

@Serializable
data class MediocreChapterSimpleDto(
    @JsonNames("id", "cap_id") val id: Int = 0,
    @JsonNames("nome", "cap_nome") val name: String = "",
    @JsonNames("numero", "cap_num") val number: Float? = null,
    @JsonNames("imagem", "cap_imagem") val image: String? = null,
    @JsonNames("lancado_em", "cap_lancado_em", "publicado_em") val releasedAt: String? = null,
    @JsonNames("criado_em", "cap_criado_em") val createdAt: String? = null,
    @JsonNames("descricao", "cap_desc") val description: String? = null,
    @JsonNames("tem_paginas") val hasPages: Boolean = false,
    val totallinks: Int = 0,
    @SerialName("lido") val read: Boolean = false,
    val views: Int = 0,
)

@Serializable
data class MediocreMangaDto(
    @JsonNames("id", "obr_id") val id: Int = 0,
    val slug: String? = null,
    @JsonNames("nome", "obr_nome") val name: String = "",
    @JsonNames("descricao", "obr_descricao") val description: String? = null,
    @JsonNames("imagem", "obr_imagem") val image: String? = null,
    val tags: List<MediocreTagDto> = emptyList(),
    val status: MediocreStatusDto? = null,
    @SerialName("obr_status") val statusName: String? = null,
    @JsonNames("total_capitulos", "capitulos_count", "total_capitulos_ativos") val totalChapters: Int = 0,
    @SerialName("capitulos") val chapters: List<MediocreChapterSimpleDto> = emptyList(),
)

@Serializable
data class MediocrePageSrcDto(
    @JsonNames("src", "url") val src: String = "",
    val ordem: Int = 0,
)

@Serializable
data class MediocreChapterDetailDto(
    @JsonNames("id", "cap_id") val id: Int = 0,
    @JsonNames("uuid", "cap_uuid") val uuid: String? = null,
    @JsonNames("nome", "cap_nome") val name: String = "",
    @JsonNames("numero", "cap_num") val number: Float? = null,
    @JsonNames("imagem", "cap_imagem") val image: String? = null,
    @JsonNames("paginas", "paginas2") val pages: List<MediocrePageSrcDto> = emptyList(),
    @JsonNames("lancado_em", "cap_lancado_em") val releasedAt: String? = null,
    @JsonNames("criado_em", "cap_criado_em") val createdAt: String? = null,
    @SerialName("obra") val manga: MediocreMangaDto? = null,
)

fun MediocreMangaDto.toSManga(isDetails: Boolean = false): SManga {
    val sManga = SManga.create().apply {
        title = name
        thumbnail_url = image?.let {
            when {
                it.startsWith("http") -> it
                else -> "${MediocreToons.CDN_URL}/obras/${this@toSManga.id}/$it"
            }
        }
        initialized = isDetails
        url = "/obra/$id"
        genre = tags.joinToString { it.name }
    }

    description?.let { sManga.description = Jsoup.parseBodyFragment(it).text() }

    val statusText = status?.name ?: statusName
    statusText?.let {
        sManga.status = when (it.lowercase()) {
            "em andamento", "ativo", "em_lancamento", "em lançamento" -> SManga.ONGOING
            "completo", "concluído", "concluido" -> SManga.COMPLETED
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
    date_upload = dateFormat.tryParse(releasedAt ?: createdAt)
}

fun MediocreChapterDetailDto.toPageList(): List<Page> {
    val obraId = manga?.id ?: 0
    val chapterNumber = number?.toString()?.removeSuffix(".0") ?: name
    return pages.sortedWith(compareBy<MediocrePageSrcDto> { it.ordem }.thenBy { it.src }).mapIndexed { idx, p ->
        val imageUrl = when {
            p.src.startsWith("http") -> p.src
            p.src.startsWith("obras/") -> "${MediocreToons.CDN_URL}/${p.src}"
            else -> "${MediocreToons.CDN_URL}/obras/$obraId/capitulos/$chapterNumber/${p.src}"
        }
        Page(idx, imageUrl = imageUrl)
    }
}

fun MediocreChapterDetailDto.toCdnPageListUrl(): String? {
    val obraId = manga?.id ?: return null
    val chapterUuid = uuid ?: return null
    val chapterNumber = number?.toString()?.removeSuffix(".0") ?: return null
    return "${MediocreToons.CDN_URL}/obras/$obraId/capitulos/$chapterNumber/$chapterUuid.json"
}

private val dateFormat by lazy {
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
}
