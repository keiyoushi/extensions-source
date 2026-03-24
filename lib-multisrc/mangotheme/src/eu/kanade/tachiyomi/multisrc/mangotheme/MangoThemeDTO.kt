package eu.kanade.tachiyomi.multisrc.mangotheme

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Serializable
class MangoThemeResponse<T>(
    @JsonNames("sucesso")
    val success: Boolean = false,
    @JsonNames("dados", "obras", "obra", "data", "capitulos", "capitulo", "formatos", "status", "tags")
    val payload: T? = null,
    val pagination: MangoThemePaginationDto? = null,
) {
    val items: T
        get() = payload ?: throw NoSuchElementException("Response payload not found")
}

@Serializable
class MangoThemePaginationDto(
    @JsonNames("pagina")
    val page: Int = 0,
    @JsonNames("limite")
    val limit: Int = 0,
    val total: Int = 0,
    @JsonNames("totalPaginas")
    val totalPages: Int = 0,
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
    @JsonNames("descricao")
    val description: String? = null,
    @JsonNames("formato_id")
    val formatId: Int? = null,
    @JsonNames("status_id")
    val statusId: Int? = null,
    @JsonNames("total_capitulos")
    val totalChapters: Int? = null,
    @JsonNames("criada_em")
    val createdAt: String? = null,
    @JsonNames("atualizada_em")
    val updatedAt: String? = null,
    @JsonNames("banner_imagem")
    val bannerImage: String? = null,
    @JsonNames("formato_nome")
    val formatName: String? = null,
    @JsonNames("status_nome")
    val statusName: String? = null,
    val tags: List<MangoThemeTagDto> = emptyList(),
    @JsonNames("capitulos")
    val chapters: List<MangoThemeChapterDto> = emptyList(),
) {
    fun toSManga(cdnUrl: String, fallbackSlug: String? = null): SManga = SManga.create().apply {
        title = this@MangoThemeMangaDto.title
        url = buildInternalMangaUrl(
            mangaId = id ?: error("Missing manga id"),
            mangaSlug = slug ?: fallbackSlug,
        )
        thumbnail_url = (coverImage ?: bannerImage).toAbsoluteUrl(cdnUrl)
        description = this@MangoThemeMangaDto.description?.takeIf { it.isNotBlank() }
        genre = tags.map { it.name.trim() }
            .takeIf { it.isNotEmpty() }
            ?.joinToString()
        status = parseStatus(statusName, statusId)
    }
}

@Serializable
class MangoThemeTagDto(
    val id: Int,
    @JsonNames("nome", "name")
    val name: String,
    @JsonNames("criado_em")
    val createdAt: String? = null,
    val color: String? = null,
)

@Serializable
class MangoThemeChapterDto(
    val id: Int? = null,
    @JsonNames("obra_id")
    val mangaId: Int,
    @JsonNames("numero")
    @Serializable(with = StringOrNumberSerializer::class)
    val number: String,
    @JsonNames("nome", "title")
    val title: String? = null,
    val paywall: Boolean? = null,
    @JsonNames("data_fim_paywall")
    val paywallEndDate: String? = null,
    @JsonNames("criado_em")
    val createdAt: String? = null,
    @JsonNames("atualizado_em")
    val updatedAt: String? = null,
    @JsonNames("total_paginas")
    val totalPages: Int? = null,
) {
    fun toSChapter(mangaSlug: String? = null): SChapter = SChapter.create().apply {
        val formattedNumber = number.formatChapterNumber()
        name = "Capitulo $formattedNumber"
        chapter_number = number.toFloatOrNull() ?: -1f
        url = buildInternalChapterUrl(
            mangaId = mangaId,
            chapterNumber = formattedNumber,
            mangaSlug = mangaSlug,
        )
        date_upload = parseApiDate(createdAt ?: updatedAt)
    }
}

@Serializable
class MangoThemePageChapterDto(
    val id: Int? = null,
    @JsonNames("obra_id")
    val mangaId: Int,
    @JsonNames("numero")
    @Serializable(with = StringOrNumberSerializer::class)
    val number: String,
    @JsonNames("nome", "title")
    val title: String? = null,
    @JsonNames("paginas")
    val pages: List<MangoThemePageDto> = emptyList(),
    @JsonNames("criado_em")
    val createdAt: String? = null,
    @JsonNames("atualizado_em")
    val updatedAt: String? = null,
)

@Serializable
class MangoThemePageDto(
    @JsonNames("numero")
    val number: Int,
    @JsonNames("cdn_id", "imagem", "image", "src", "link", "path", "arquivo")
    val url: String? = null,
)

@Serializable
class MangoThemeLoginResponseDto(
    @JsonNames("sucesso")
    val success: Boolean = false,
    @JsonNames("token", "access_token")
    val token: String? = null,
)

@Serializable
class MangoThemeAuthRequestDto(
    val email: String,
    @SerialName("senha")
    val password: String,
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

private object StringOrNumberSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleString", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String = when (decoder) {
        is JsonDecoder -> decoder.decodeJsonElement().jsonPrimitive.content
        else -> decoder.decodeString()
    }

    override fun serialize(encoder: Encoder, value: String) {
        encoder.encodeString(value)
    }
}
