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
class MediocreListDto<T>(
    val data: T,
    val pagination: MediocrePaginationDto? = null,
)

@Serializable
class MediocrePaginationDto(
    val currentPage: Int? = null,
    val totalPages: Int = 0,
    val totalItems: Int = 0,
    val itemsPerPage: Int = 0,
    val hasNextPage: Boolean = false,
    val hasPreviousPage: Boolean = false,
)

@Serializable
class MediocreTagDto(
    val id: Int = 0,
    @SerialName("nome") val name: String = "",
)

@Serializable
class MediocreFormatDto(
    val id: Int = 0,
    @SerialName("nome") val name: String = "",
)

@Serializable
class MediocreStatusDto(
    val id: Int = 0,
    @SerialName("nome") val name: String = "",
)

@Serializable
class MediocreChapterSimpleDto(
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
class MediocreMangaDto(
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
class MediocrePageSrcDto(
    val src: String = "",
)

@Serializable
class MediocreChapterMangaDto(
    val id: Int = 0,
    @SerialName("nome") val name: String = "",
    val vip: Boolean = false,
    @SerialName("desativada") val disabled: Boolean = false,
)

@Serializable
class MediocreChapterDetailDto(
    val id: Int = 0,
    @SerialName("nome") val name: String = "",
    @SerialName("numero") val number: Float? = null,
    @SerialName("imagem") val image: String? = null,
    @SerialName("paginas") val pages: List<MediocrePageSrcDto> = emptyList(),
    @SerialName("lancado_em") val releasedAt: String? = null,
    @SerialName("criado_em") val createdAt: String? = null,
    @SerialName("obra") val manga: MediocreChapterMangaDto? = null,
)

fun MediocreMangaDto.toSManga(isDetails: Boolean = false): SManga {
    return SManga.create().apply {
        title = this@toSManga.name
        thumbnail_url = this@toSManga.image?.let {
            when {
                it.startsWith("http") -> it
                else -> "${MediocreToons.CDN_URL}/obras/${this@toSManga.id}/$it"
            }
        }
        initialized = isDetails
        url = "/obra/$id"
        genre = tags.joinToString { it.name }

        this@toSManga.description?.let { desc ->
            description = Jsoup.parseBodyFragment(desc).text()
        }

        this@toSManga.status?.let {
            status = when (it.name.lowercase()) {
                "em andamento", "ativo" -> SManga.ONGOING
                "completo", "concluído" -> SManga.COMPLETED
                "hiato" -> SManga.ON_HIATUS
                "cancelada" -> SManga.CANCELLED
                else -> SManga.UNKNOWN
            }
        }
    }
}

fun MediocreChapterSimpleDto.toSChapter(): SChapter {
    return SChapter.create().apply {
        name = this@toSChapter.name
        chapter_number = number ?: 0f
        url = "/capitulo/$id"
        date_upload = dateFormat.tryParse(createdAt)
    }
}

fun MediocreChapterDetailDto.toPageList(): List<Page> {
    val mangaId = manga?.id ?: 0
    val chapterNumber = number?.toString()?.removeSuffix(".0") ?: name

    return pages.mapIndexed { idx, p ->
        val imageUrl = "${MediocreToons.CDN_URL}/obras/$mangaId/capitulos/$chapterNumber/${p.src}"
        Page(idx, imageUrl = imageUrl)
    }
}

private val dateFormat by lazy {
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
}
