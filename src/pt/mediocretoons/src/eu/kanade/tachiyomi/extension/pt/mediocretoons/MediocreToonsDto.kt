package eu.kanade.tachiyomi.extension.pt.mediocretoons

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup

@Serializable
data class MediocrePaginationDto(
    val currentPage: Int? = null,
    val totalPages: Int = 0,
    val totalItems: Int = 0,
    val itemsPerPage: Int = 0,
    val hasNextPage: Boolean = false,
    val hasPreviousPage: Boolean = false,
)

@Serializable
data class MediocreListDto<T>(
    val data: T,
    val pagination: MediocrePaginationDto? = null,
)

@Serializable
data class MediocreTagDto(
    val id: Int = 0,
    val nome: String = "",
)

@Serializable
data class MediocreFormatDto(
    val id: Int = 0,
    val nome: String = "",
)

@Serializable
data class MediocreStatusDto(
    val id: Int = 0,
    val nome: String = "",
)

@Serializable
data class MediocreChapterSimpleDto(
    val id: Int = 0,
    val nome: String = "",
    val numero: Float? = null,
    val imagem: String? = null,
    val lancado_em: String? = null,
    val criado_em: String? = null,
    val descricao: String? = null,
    val tem_paginas: Boolean = false,
    val totallinks: Int = 0,
    val lido: Boolean = false,
    val views: Int = 0,
)

@Serializable
data class MediocreMangaDto(
    val id: Int = 0,
    val nome: String = "",
    val descricao: String? = null,
    val imagem: String? = null,
    val formato: MediocreFormatDto? = null,
    val tags: List<MediocreTagDto> = emptyList(),
    val status: MediocreStatusDto? = null,
    val total_capitulos: Int = 0,
    val capitulos: List<MediocreChapterSimpleDto> = emptyList(),
)

@Serializable
data class MediocrePageSrcDto(
    val src: String = "",
)

@Serializable
data class MediocreChapterDetailDto(
    val id: Int = 0,
    val nome: String = "",
    val numero: Float? = null,
    val imagem: String? = null,
    val paginas: List<MediocrePageSrcDto> = emptyList(),
    val lancado_em: String? = null,
    val criado_em: String? = null,
    val obra: MediocreMangaDto? = null,
)

fun MediocreMangaDto.toSManga(): SManga {
    val sManga = SManga.create().apply {
        title = nome
        thumbnail_url = imagem?.let {
            when {
                it.startsWith("http") -> it
                else -> "${MediocreToons.CDN_URL}/obras/${this@toSManga.id}/$it?v=3"
            }
        }
        initialized = true
        url = "/obra/${this@toSManga.id}"
        genre = tags.joinToString { it.nome }
    }
    descricao?.let { Jsoup.parseBodyFragment(it).let { sManga.description = it.text() } }
    sManga.status = status?.let {
        when (it.nome.lowercase()) {
            "em andamento" -> SManga.ONGOING
            "completo" -> SManga.COMPLETED
            "hiato" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    } ?: SManga.UNKNOWN
    return sManga
}

fun MediocreChapterSimpleDto.toSChapter(): SChapter {
    return SChapter.create().apply {
        name = nome
        chapter_number = numero ?: 0f
        url = "/capitulo/$id"
        date_upload = criado_em?.let { MediocreToonsDateParser.parse(it) } ?: 0
    }
}

fun MediocreChapterDetailDto.toPageList(): List<Page> {
    val obraId = obra?.id ?: 0
    val capituloNome = nome
    return paginas.mapIndexed { idx, p ->
        val imageUrl = "${MediocreToons.CDN_URL}/obras/$obraId/capitulos/$capituloNome/${p.src}"
        Page(idx, imageUrl = imageUrl)
    }
}

object MediocreToonsDateParser {
    private val format = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
        timeZone = java.util.TimeZone.getTimeZone("UTC")
    }

    fun parse(value: String): Long {
        return try {
            format.parse(value)?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
    }
}
