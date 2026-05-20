package eu.kanade.tachiyomi.extension.pt.mediocretoons

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

// ============================== Base DTOs ===============================
@Serializable
data class MediocrePaginationDto(
    @JsonNames("currentPage", "pagina_atual") val currentPage: Int? = null,
    @JsonNames("totalPages", "paginas") val totalPages: Int = 0,
    @JsonNames("totalItems", "total") val totalItems: Int = 0,
    @JsonNames("itemsPerPage", "itens_por_pagina") val itemsPerPage: Int = 0,
) {
    val hasNextPage: Boolean get() = totalPages > (currentPage ?: 0)
}

@Serializable
data class MediocreListDto<T>(
    val data: T,
    val pagination: MediocrePaginationDto? = null,
)

// ============================== Common DTOs ===============================
@Serializable
data class MediocreTagDto(
    val id: Int = 0,
    @SerialName("nome") val name: String = "",
)

@Serializable
data class MediocreFormatDto(
    @SerialName("formt_id") val id: Int = 0,
    @SerialName("formt_nome") val name: String = "",
)

@Serializable
data class MediocreStatusDto(
    val id: Int = 0,
    @SerialName("nome") val name: String = "",
)

// ============================== Manga DTOs ===============================
@Serializable
data class MediocreMangaSimpleDto(
    val id: Int = 0,
    val slug: String? = null,
    @SerialName("nome") val name: String = "",
    @SerialName("imagem") val image: String? = null,
    @SerialName("formato") val formato: String? = null,
    @SerialName("total_capitulos") val totalChapters: Int = 0,
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
    @SerialName("status") val status: MediocreStatusDto? = null,
    @SerialName("total_capitulos") val totalChapters: Int = 0,
    @SerialName("capitulos") val chapters: List<MediocreChapterSimpleDto> = emptyList(),
)

@Serializable
data class MediocreMangaDetailsDto(
    @SerialName("obr_id") val id: Int = 0,
    @SerialName("obr_nome") val name: String = "",
    @SerialName("obr_descricao") val descriptionText: String? = null,
    @SerialName("obr_status") val obStatus: String = "",
    @SerialName("obr_imagem") val image: String? = null,
    @SerialName("formato") val format: MediocreFormatDto? = null,
    @SerialName("capitulos") val chapters: List<MediocreChapterFromMangaDto> = emptyList(),
    @SerialName("tags") val tags: List<MediocreTagDto> = emptyList(),
)

// ============================== Chapter DTOs ===============================
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
data class MediocreChapterFromMangaDto(
    @SerialName("cap_id") val id: Int = 0,
    @SerialName("cap_nome") val name: String = "",
    @SerialName("cap_num") val number: Float = 0f,
    @SerialName("cap_criado_em") val createdAt: String? = null,
)

// ============================== Page DTOs ===============================
@Serializable
data class MediocrePageDto(
    val ordem: Int = 0,
    val url: String = "",
)

// ============================== Extension Functions ===============================
fun MediocreMangaSimpleDto.toSManga(): SManga = SManga.create().apply {
    title = name
    thumbnail_url = image?.let {
        if (it.startsWith("http")) it else "${MediocreToons.CDN_URL}/obras/$id/$it?v=3"
    }
    url = "/obra/$id"
    initialized = false
}

fun MediocreMangaDto.toSManga(isDetails: Boolean = false): SManga {
    val mangaId = id
    val statusObj = status

    return SManga.create().apply {
        title = name
        thumbnail_url = image?.let {
            if (it.startsWith("http")) it else "${MediocreToons.CDN_URL}/obras/$mangaId/$it?v=3"
        }
        url = "/obra/$mangaId"
        initialized = isDetails
        genre = tags.joinToString { it.name }
        description?.let { this.description = Jsoup.parseBodyFragment(it).text() }

        statusObj?.let {
            this.status = when (it.name.lowercase(Locale.ROOT)) {
                "em lançamento", "em andamento", "ativo" -> SManga.ONGOING
                "completo", "concluído" -> SManga.COMPLETED
                "hiato" -> SManga.ON_HIATUS
                "cancelada" -> SManga.CANCELLED
                else -> SManga.UNKNOWN
            }
        }
    }
}

fun MediocreMangaDetailsDto.toSManga(): SManga = SManga.create().apply {
    title = name
    thumbnail_url = image?.let {
        if (it.startsWith("http")) it else "${MediocreToons.CDN_URL}/obras/$id/$it?v=3"
    }
    url = "/obra/$id"
    initialized = true
    genre = tags.joinToString { it.name }

    status = when (obStatus) {
        "em andamento", "ativo", "em_lancamento" -> SManga.ONGOING
        "completo", "concluído" -> SManga.COMPLETED
        "hiato" -> SManga.ON_HIATUS
        "cancelada" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    descriptionText?.let {
        this.description = Jsoup.parseBodyFragment(it).text()
    }
}

fun MediocreChapterSimpleDto.toSChapter(): SChapter {
    val chapterId = id
    val chapterName = name
    val chapterNumber = number ?: 0f
    val chapterCreatedAt = createdAt

    return SChapter.create().apply {
        name = chapterName
        chapter_number = chapterNumber
        url = "/capitulo/$chapterId"
        date_upload = dateFormat.tryParse(chapterCreatedAt)
    }
}

fun MediocreChapterFromMangaDto.toSChapter(): SChapter {
    val chapterId = id
    val chapterName = name
    val chapterNumber = number
    val chapterCreatedAt = createdAt

    return SChapter.create().apply {
        name = chapterName
        chapter_number = chapterNumber
        url = "/capitulo/$chapterId"
        date_upload = dateFormat.tryParse(chapterCreatedAt)
    }
}

// ============================== Date Format ===============================
private val dateFormat by lazy {
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
}
