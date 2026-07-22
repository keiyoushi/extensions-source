package eu.kanade.tachiyomi.extension.pt.egotoons

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import kotlin.time.Instant

@Serializable
class MangaListDto(
    @SerialName("obras") val mangas: List<MangaDto>,
    val pagination: PaginationDto,
)

@Serializable
class MangaDetailsDto(
    @SerialName("obra") val manga: MangaDto,
)

@Serializable
class PaginationDto(
    val hasNextPage: Boolean,
    @SerialName("totalPaginas")
    val totalPages: Int,
)

@Serializable
class MangaDto(
    private val id: Int,
    @SerialName("nome") private val title: String,
    @SerialName("imagem") private val cover: String? = null,
    @SerialName("descricao") private val description: String? = null,
    @SerialName("formato_nome") private val format: String? = null,
    @SerialName("status_nome") private val status: String? = null,
    private val tags: List<FilterOptionDto> = emptyList(),
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        url = "/obra/$id"
        title = this@MangaDto.title
        thumbnail_url = cover?.let { baseUrl.toHttpUrl().resolve(it)?.toString() }
        description = this@MangaDto.description?.let { Jsoup.parseBodyFragment(it, baseUrl).text() }
        genre = buildList {
            format?.takeIf(String::isNotBlank)?.let(::add)
            tags.mapTo(this) { it.name }
        }.distinct().joinToString()
        status = when (this@MangaDto.status?.lowercase()) {
            "ativo", "em andamento", "em-andamento" -> SManga.ONGOING
            "finalizado", "completo" -> SManga.COMPLETED
            "hiato" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }
}

@Serializable
class ChapterListDto(
    @SerialName("capitulos") val chapters: List<ChapterDto>,
    val pagination: PaginationDto,
)

@Serializable
class ChapterDetailsDto(
    @SerialName("capitulo") val chapter: ChapterDto,
)

@Serializable
class ChapterDto(
    @SerialName("obra_id") private val mangaId: Int,
    @SerialName("numero") private val number: String,
    @SerialName("numero_key") private val numberKey: String? = null,
    @SerialName("titulo") private val title: String? = null,
    @SerialName("nome") private val name: String? = null,
    @SerialName("total_paginas") private val totalPages: Int = 0,
    @SerialName("page_url_template") private val pageUrlTemplate: String? = null,
    @SerialName("paginas") private val pages: List<String> = emptyList(),
    @SerialName("criado_em") private val createdAt: String? = null,
) {
    fun toSChapter() = SChapter.create().apply {
        val slug = numberKey ?: number.removeSuffix(".00").removeSuffix(".0")
        url = "/obra/$mangaId/capitulo/$slug"
        this.name = title?.takeIf(String::isNotBlank)
            ?: this@ChapterDto.name?.takeIf(String::isNotBlank)
            ?: "Capítulo $slug"
        chapter_number = number.toFloatOrNull() ?: -1F
        date_upload = createdAt?.let(Instant::parseOrNull)?.toEpochMilliseconds() ?: 0L
    }

    fun toPageList(baseUrl: String): List<Page> {
        val urls = pages.ifEmpty {
            pageUrlTemplate?.let { template ->
                (0 until totalPages).map { template.replace("{index}", it.toString()) }
            }.orEmpty()
        }
        val siteUrl = baseUrl.toHttpUrl()
        return urls.mapIndexed { index, url ->
            Page(index, imageUrl = requireNotNull(siteUrl.resolve(url)).toString())
        }
    }
}

@Serializable
class FilterData(
    val formats: List<FilterOptionDto>,
    val statuses: List<FilterOptionDto>,
    val tags: List<FilterOptionDto>,
)

@Serializable
class FormatListDto(
    @SerialName("formatos") val formats: List<FilterOptionDto>,
)

@Serializable
class StatusListDto(
    @SerialName("status") val statuses: List<FilterOptionDto>,
)

@Serializable
class TagListDto(
    val tags: List<FilterOptionDto>,
)

@Serializable
class FilterOptionDto(
    val id: Int,
    @SerialName("nome") val name: String,
)
