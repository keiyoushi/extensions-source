package eu.kanade.tachiyomi.multisrc.mangotheme

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Serializable
class MangoThemeResponse<T>(
    val sucesso: Boolean = false,
    val dados: T? = null,
    val obras: T? = null,
    val obra: T? = null,
    val data: T? = null,
    val capitulos: T? = null,
    val capitulo: T? = null,
    val formatos: T? = null,
    val status: T? = null,
    val tags: T? = null,
    val pagination: MangoThemePaginationDto? = null,
) {
    val items: T
        get() = dados ?: obras ?: obra ?: data ?: capitulos ?: capitulo ?: formatos ?: status ?: tags
            ?: throw NoSuchElementException("Response payload not found")
}

@Serializable
class MangoThemePaginationDto(
    val pagina: Int = 0,
    val limite: Int = 0,
    val total: Int = 0,
    val totalPaginas: Int = 0,
    val hasNextPage: Boolean = false,
    val hasPreviousPage: Boolean = false,
)

@Serializable
class MangoThemeMangaDto(
    val id: Int? = null,
    @JsonNames("title", "nome")
    val title: String,
    @JsonNames("slug", "nome_url", "permalink", "url")
    val slug: String? = null,
    @JsonNames("coverImage", "imagem")
    val coverImage: String? = null,
    val descricao: String? = null,
    val formato_id: Int? = null,
    val status_id: Int? = null,
    val total_capitulos: Int? = null,
    val criada_em: String? = null,
    val atualizada_em: String? = null,
    val banner_imagem: String? = null,
    val formato_nome: String? = null,
    val status_nome: String? = null,
    val tags: List<MangoThemeTagDto> = emptyList(),
    val capitulos: List<MangoThemeChapterDto> = emptyList(),
) {
    fun toSManga(cdnUrl: String, fallbackSlug: String? = null): SManga = SManga.create().apply {
        title = this@MangoThemeMangaDto.title
        url = buildInternalMangaUrl(
            mangaId = id ?: error("Missing manga id"),
            mangaSlug = slug ?: fallbackSlug,
        )
        thumbnail_url = (coverImage ?: banner_imagem).toAbsoluteUrl(cdnUrl)
        description = descricao?.takeIf { it.isNotBlank() }
        genre = tags.map { it.name.trim() }
            .takeIf { it.isNotEmpty() }
            ?.joinToString()
        status = parseStatus(status_nome, status_id)
    }
}

@Serializable
class MangoThemeTagDto(
    val id: Int,
    @JsonNames("nome", "name")
    val name: String,
    val criado_em: String? = null,
    val color: String? = null,
)

@Serializable
class MangoThemeChapterDto(
    val id: Int? = null,
    val obra_id: Int,
    val numero: String,
    @JsonNames("nome", "title")
    val title: String? = null,
    val paywall: Boolean? = null,
    val data_fim_paywall: String? = null,
    val criado_em: String? = null,
    val atualizado_em: String? = null,
    val total_paginas: Int? = null,
) {
    fun toSChapter(mangaSlug: String? = null): SChapter = SChapter.create().apply {
        val formattedNumber = numero.formatChapterNumber()
        name = "Capitulo $formattedNumber"
        chapter_number = numero.toFloatOrNull() ?: -1f
        url = buildInternalChapterUrl(
            mangaId = obra_id,
            chapterNumber = formattedNumber,
            mangaSlug = mangaSlug,
        )
        date_upload = parseApiDate(criado_em ?: atualizado_em)
    }
}

@Serializable
class MangoThemePageChapterDto(
    val id: Int? = null,
    val obra_id: Int,
    val numero: String,
    val nome: String? = null,
    val paginas: List<MangoThemePageDto> = emptyList(),
    val criado_em: String? = null,
    val atualizado_em: String? = null,
)

@Serializable
class MangoThemePageDto(
    val numero: Int,
    @JsonNames("cdn_id", "imagem", "image", "src", "link", "path", "arquivo")
    val url: String? = null,
)

@Serializable
class MangoThemeLoginResponseDto(
    val sucesso: Boolean = false,
    @JsonNames("token", "access_token")
    val token: String? = null,
)

@Serializable
class MangoThemeAuthRequestDto(
    val email: String,
    val senha: String,
)

internal fun String.formatChapterNumber(): String = toFloatOrNull()
    ?.toString()
    ?.removeSuffix(".0")
    ?: this

private fun buildInternalMangaUrl(mangaId: Int, mangaSlug: String?): String = buildString {
    append("/obra/$mangaId")
    mangaSlug.toStoredSlug()?.let { append("?slug=$it") }
}

private fun buildInternalChapterUrl(mangaId: Int, chapterNumber: String, mangaSlug: String?): String = buildString {
    append("/obra/$mangaId/capitulo/$chapterNumber")
    mangaSlug.toStoredSlug()?.let { append("?slug=$it") }
}

internal fun parseApiDate(dateString: String?): Long {
    val normalizedDate = dateString
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.normalizeMidnightOverflow()
        ?: return 0L

    return DATE_FORMATTERS
        .asSequence()
        .map { formatter -> formatter.tryParse(normalizedDate) }
        .firstOrNull { it != 0L }
        ?: 0L
}

private fun String.normalizeMidnightOverflow(): String {
    val match = MIDNIGHT_OVERFLOW_REGEX.matchEntire(this) ?: return this
    val parsedDate = DATE_ONLY_FORMAT.tryParse(match.groupValues[1])
        .takeIf { it != 0L }
        ?: return this
    val nextDay = Calendar.getInstance().apply {
        timeInMillis = parsedDate
        add(Calendar.DAY_OF_MONTH, 1)
    }

    return buildString {
        append(DATE_ONLY_FORMAT.format(nextDay.time))
        append('T')
        append("00:")
        append(match.groupValues[2])
        append(match.groupValues[3])
    }
}

private fun String?.toAbsoluteUrl(baseUrl: String): String? = this
    ?.takeIf { it.isNotBlank() }
    ?.let { imageUrl ->
        imageUrl.takeIf { it.toHttpUrlOrNull() != null } ?: "$baseUrl/${imageUrl.trimStart('/')}"
    }

private fun String?.toStoredSlug(): String? = this
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
    ?.substringBefore('?')
    ?.trimEnd('/')
    ?.substringAfterLast('/')
    ?.takeIf { it.isNotEmpty() }

private fun parseStatus(statusName: String?, statusId: Int?): Int = when (statusName?.trim()) {
    "Ativo", "Em Andamento" -> SManga.ONGOING
    "Conclu\u00eddo" -> SManga.COMPLETED
    "Hiato", "Pausado" -> SManga.ON_HIATUS
    "Cancelado" -> SManga.CANCELLED
    else -> when (statusId) {
        1, 6 -> SManga.ONGOING
        2, 5 -> SManga.ON_HIATUS
        3 -> SManga.COMPLETED
        4 -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }
}

private val MIDNIGHT_OVERFLOW_REGEX =
    Regex("""^(\d{4}-\d{2}-\d{2})T24:(\d{2}:\d{2}(?:\.\d{1,3})?)(.*)$""")

private val DATE_ONLY_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)

private val DATE_FORMATTERS = listOf(
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.ROOT),
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.ROOT),
)
