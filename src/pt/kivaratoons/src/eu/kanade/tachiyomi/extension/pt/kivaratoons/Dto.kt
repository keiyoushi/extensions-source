package eu.kanade.tachiyomi.extension.pt.kivaratoons

import android.util.Base64
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
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
class LatestUpdatesDto(
    private val updates: List<ChapterUpdateDto>,
    @SerialName("pagina") private val page: Int = 1,
    @SerialName("total_paginas") private val totalPages: Int = 1,
) {
    val mangas get() = updates.distinctBy(ChapterUpdateDto::mangaId)
    val hasNextPage get() = page < totalPages
}

@Serializable
class ChapterUpdateDto(
    @SerialName("obra_id") private val id: JsonPrimitive,
    @SerialName("obra_nome") private val mangaName: String,
    @SerialName("obra_capa") private val mangaCover: String? = null,
) {
    val mangaId get() = id.content.decodeMangaId()

    fun toSManga(siteUrl: HttpUrl) = SManga.create().apply {
        url = mangaId
        title = mangaName
        thumbnail_url = mangaCover?.takeIf(String::isNotBlank)?.let { siteUrl.resolve(it)?.toString() }
    }
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
    @SerialName("obra_id") private val mangaId: JsonPrimitive,
    @SerialName("numero") private val number: Float,
    @SerialName("data_publicacao") private val publishedAt: String? = null,
) {
    fun toSChapter() = SChapter.create().apply {
        url = id
        name = "Capítulo ${number.formatted()}"
        chapter_number = number
        date_upload = publishedAt?.let(Instant::parseOrNull)?.toEpochMilliseconds() ?: 0L
        memo = buildJsonObject { put("mangaId", mangaId.content.decodeMangaId()) }
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

private const val MANGA_ID_KEY = "4b7e8"

/** Newer entries expose the manga id base64 encoded and XORed, while the listings use the plain numeric id. */
fun String.decodeMangaId(): String {
    if (isEmpty() || all(Char::isDigit)) return this

    val bytes = runCatching { Base64.decode(this, Base64.DEFAULT) }.getOrNull() ?: return this
    val decoded = String(
        ByteArray(bytes.size) { (bytes[it].toInt() xor MANGA_ID_KEY[it % MANGA_ID_KEY.length].code).toByte() },
    )

    return decoded.takeIf { it.isNotEmpty() && it.all(Char::isDigit) } ?: this
}
