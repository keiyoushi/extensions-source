package eu.kanade.tachiyomi.extension.pt.kivaratoons

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import kotlin.time.Instant

@Serializable
class MangaListDto(
    @SerialName("obras") val mangas: List<MangaDto>,
    @SerialName("pagina") private val page: Int = 1,
    @SerialName("total_paginas") private val totalPages: Int = 1,
) {
    val hasNextPage get() = page < totalPages
}

@Serializable
class MangaDto(
    private val id: Int,
    @SerialName("nome") private val name: String,
    @SerialName("nome_alternativo") private val alternativeName: String? = null,
    @SerialName("capa_url") private val cover: String? = null,
    @SerialName("descricao") private val description: String? = null,
    @SerialName("autor") private val author: String? = null,
    @SerialName("artista") private val artist: String? = null,
    @SerialName("status_id") private val statusId: Int = 0,
    private val tags: List<String> = emptyList(),
    @SerialName("capitulos") val chapters: List<ChapterDto> = emptyList(),
) {
    fun toSManga(siteUrl: HttpUrl) = SManga.create().apply {
        url = id.toString()
        title = name
        thumbnail_url = cover?.takeIf(String::isNotBlank)?.let { siteUrl.resolve(it)?.toString() }
        author = this@MangaDto.author?.takeIf(String::isNotBlank)
        artist = this@MangaDto.artist?.takeIf(String::isNotBlank)
        genre = tags.joinToString()
        status = when (statusId) {
            1 -> SManga.ONGOING
            2 -> SManga.COMPLETED
            3 -> SManga.ON_HIATUS
            4 -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        description = buildString {
            this@MangaDto.description?.takeIf(String::isNotBlank)?.let(::appendLine)
            alternativeName?.takeIf(String::isNotBlank)?.let {
                appendLine()
                append("Título alternativo: ", it)
            }
        }.trim()
    }
}

@Serializable
class ChapterDto(
    private val id: String,
    @SerialName("obra_id") private val mangaId: Int,
    @SerialName("numero") private val number: Float,
    @SerialName("data_publicacao") private val publishedAt: String? = null,
) {
    fun toSChapter() = SChapter.create().apply {
        url = id
        name = "Capítulo ${number.formatted()}"
        chapter_number = number
        date_upload = publishedAt?.let(Instant::parseOrNull)?.toEpochMilliseconds() ?: 0L
        memo = buildJsonObject { put("mangaId", mangaId) }
    }

    private fun Float.formatted(): String = if (this % 1 == 0f) toInt().toString() else toString()
}

@Serializable
class ChapterPagesDto(
    @SerialName("paginas") private val pages: List<PageDto> = emptyList(),
) {
    fun toPageList(siteUrl: HttpUrl) = pages.sortedBy { it.order }
        .mapNotNull { page -> siteUrl.resolve(page.url)?.toString() }
        .mapIndexed { index, imageUrl -> Page(index, imageUrl = imageUrl) }
}

@Serializable
class PageDto(
    @SerialName("ordem") val order: Int = 0,
    val url: String,
)

@Serializable
class FilterOptionDto(
    val id: Int,
    @SerialName("nome") val name: String,
)

@Serializable
class FormatListDto(
    @SerialName("formatos") val formats: List<FilterOptionDto> = emptyList(),
)

@Serializable
class StatusListDto(
    val status: List<FilterOptionDto> = emptyList(),
)

@Serializable
class TagListDto(
    val tags: List<String> = emptyList(),
)

@Serializable
class FilterData(
    val formats: List<FilterOptionDto>,
    val statuses: List<FilterOptionDto>,
    val tags: List<String>,
)
