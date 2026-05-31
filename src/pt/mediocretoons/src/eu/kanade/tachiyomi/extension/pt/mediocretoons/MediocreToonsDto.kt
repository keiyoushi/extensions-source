package eu.kanade.tachiyomi.extension.pt.mediocretoons

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Serializable
data class MediocrePaginationDto(
    val currentPage: Int? = null,
    val totalPages: Int = 0,
    val totalItems: Int = 0,
    val itemsPerPage: Int = 0,
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
    @SerialName("tag_id") val tagId: Int = 0,
    val nome: String = "",
    @SerialName("tag_nome") val tagName: String = "",
) {
    val name: String get() = tagName.ifEmpty { nome }
}

@Serializable
data class MediocreFormatDto(
    val id: Int = 0,
    @SerialName("formt_id") val formatId: Int = 0,
    val nome: String = "",
    @SerialName("formt_nome") val formatName: String = "",
) {
    val name: String get() = formatName.ifEmpty { nome }
}

@Serializable
data class MediocreStatusDto(
    val id: Int = 0,
    val nome: String = "",
) {
    val name: String get() = nome
}

@Serializable
data class MediocreChapterSimpleDto(
    @SerialName("cap_id") val id: Int = 0,
    @SerialName("cap_nome") val name: String = "",
    @SerialName("cap_num") val number: Float? = null,
    @SerialName("cap_imagem") val image: String? = null,
    @SerialName("cap_lancado_em") val releasedAt: String? = null,
    @SerialName("cap_criado_em") val createdAt: String? = null,
    @SerialName("cap_desc") val description: String? = null,
    @SerialName("tem_paginas") val hasPages: Boolean = false,
    val totallinks: Int = 0,
    @SerialName("lido") val read: Boolean = false,
    val views: Int = 0,
)

@Serializable
data class MediocreMangaDto(
    val id: Int = 0,
    @SerialName("obr_id") val obraId: Int = 0,
    val slug: String? = null,
    val nome: String = "",
    @SerialName("obr_nome") val obraName: String = "",
    @SerialName("obr_descricao") val description: String? = null,
    val imagem: String? = null,
    @SerialName("obr_imagem") val obraImage: String? = null,
    val tags: List<MediocreTagDto> = emptyList(),
    val status: MediocreStatusDto? = null,
    @SerialName("obr_status") val statusName: String? = null,
    @SerialName("total_capitulos") val totalChapters: Int = 0,
    @SerialName("capitulos_count") val chaptersCount: Int = 0,
    @SerialName("total_capitulos_ativos") val activeChaptersCount: Int = 0,
    @SerialName("capitulos") val chapters: List<MediocreChapterSimpleDto> = emptyList(),
) {
    val mangaId: Int get() = obraId.takeIf { it != 0 } ?: id
    val name: String get() = obraName.ifEmpty { nome }
    val image: String? get() = obraImage ?: imagem
}

@Serializable
data class MediocrePageSrcDto(
    @SerialName("url") val src: String = "",
    val ordem: Int = 0,
)

@Serializable
data class MediocreChapterDetailDto(
    @SerialName("cap_id") val id: Int = 0,
    @SerialName("cap_uuid") val uuid: String? = null,
    @SerialName("cap_nome") val name: String = "",
    @SerialName("cap_num") val number: Float? = null,
    @SerialName("cap_imagem") val image: String? = null,
    @SerialName("paginas") val pages: List<MediocrePageSrcDto> = emptyList(),
    @SerialName("cap_lancado_em") val releasedAt: String? = null,
    @SerialName("cap_criado_em") val createdAt: String? = null,
    @SerialName("obra") val manga: MediocreMangaDto? = null,
)

fun MediocreMangaDto.toSManga(isDetails: Boolean = false): SManga {
    val sManga = SManga.create().apply {
        title = name
        thumbnail_url = image?.let {
            when {
                it.startsWith("http") -> it
                else -> "${MediocreToons.CDN_URL}/obras/${this@toSManga.mangaId}/$it"
            }
        }
        initialized = isDetails
        url = "/obra/$mangaId"
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
    val obraId = manga?.mangaId ?: 0
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

fun List<MediocrePageSrcDto>.toPageList(): List<Page> = sortedWith(compareBy<MediocrePageSrcDto> { it.ordem }.thenBy { it.src })
    .mapIndexedNotNull { idx, page ->
        val imageUrl = page.src.takeIf { it.isNotBlank() }?.let {
            when {
                it.startsWith("http") -> it
                else -> "${MediocreToons.CDN_URL}/$it"
            }
        }

        imageUrl?.let { Page(idx, imageUrl = it) }
    }

fun MediocreChapterDetailDto.toCdnPageListUrl(): String? {
    val obraId = manga?.mangaId ?: return null
    val chapterUuid = uuid ?: return null
    val chapterNumber = number?.toString()?.removeSuffix(".0") ?: return null
    return "${MediocreToons.CDN_URL}/obras/$obraId/capitulos/$chapterNumber/$chapterUuid.json"
}

private val dateFormat by lazy {
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
}
