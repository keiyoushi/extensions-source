package eu.kanade.tachiyomi.extension.es.jeazscans

import android.util.Base64
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class ChaptersPageDto(
    val success: Boolean = false,
    val chapters: List<ChaptersApiChapter> = emptyList(),
    @SerialName("has_more") val hasMore: Boolean = false,
    @SerialName("next_offset") val nextOffset: Int? = null,
) {
    fun toChapterPage(): ChapterPage = ChapterPage(chapters, hasMore, nextOffset)
}

@Serializable
class ChaptersApiChapter(
    val id: Long? = null,
    val number: String? = null,
    val title: String? = null,
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("is_locked") val isLocked: Boolean = false,
) {
    /**
     * Pure mapping of an API chapter record into [ChapterData], free of any
     * host-app runtime type. Returns `null` for locked (paid) records and for
     * records whose `number` cannot be parsed, so no reader entry is produced.
     */
    fun toChapterData(slug: String, baseUrl: String): ChapterData? {
        if (isLocked) return null
        val chapterNumber = number?.toFloatOrNull() ?: return null
        val chapterUrl = "$baseUrl/leer/$slug/capitulo-$number"
        val nonBlankTitle = title?.takeIf { it.isNotBlank() }
        return ChapterData(
            readerUrl = chapterUrl.substringAfter(baseUrl),
            chapterNumber = chapterNumber,
            name = nonBlankTitle ?: "Chapter ${chapterNumber.toString().removeSuffix(".0")}",
            dateUpload = parseChapterDate(publishedAt),
        )
    }

    /**
     * Materialize a host-app [SChapter] for unlocked records only, delegating
     * the pure mapping to [toChapterData].
     */
    fun toSChapter(slug: String, baseUrl: String): SChapter? {
        val data = toChapterData(slug, baseUrl) ?: return null
        return SChapter.create().apply {
            url = data.readerUrl
            chapter_number = data.chapterNumber
            name = data.name
            date_upload = data.dateUpload
        }
    }
}

/**
 * Pure, host-app-free representation of a parsed API chapter. Unit tests can
 * verify the mapping without touching `SChapter.create()`.
 */
class ChapterData(
    val readerUrl: String,
    val chapterNumber: Float,
    val name: String,
    val dateUpload: Long,
)

class ChapterPage(
    val chapters: List<ChaptersApiChapter> = emptyList(),
    val hasMore: Boolean = false,
    val nextOffset: Int? = null,
) {
    val isEmpty: Boolean get() = chapters.isEmpty()
}

@Serializable
class ApiLectorResponse(
    val success: Boolean = false,
    @SerialName("manga_titulo") val mangaTitulo: String = "",
    @SerialName("manga_slug") val mangaSlug: String = "",
    @SerialName("manga_portada") val mangaPortada: String = "",
    @SerialName("manga_tipo") val mangaTipo: String = "",
    @SerialName("cap_numero") val capNumero: String = "",
    val paginas: List<ApiLectorPage> = emptyList(),
    @SerialName("total_paginas") val totalPaginas: Int = 0,
    val anterior: String? = null,
    val siguiente: String? = null,
)

@Serializable
class ApiLectorPage(
    val orden: Int,
    @SerialName("data_verify") val dataVerify: String,
) {
    fun decodeImageUrl(): String {
        val decoded = Base64.decode(dataVerify, Base64.DEFAULT)
        return String(decoded, Charsets.UTF_8).reversed()
    }
}

@Serializable
class SearchResponseItem(
    private val id: Int,
    private val titulo: String,
    private val portada: String?,
    private val tipo: String? = null,
) {
    fun toSManga(baseUrl: String): SManga? {
        if (id == -1 || titulo.isBlank()) return null
        return SManga.create().apply {
            url = "/manga.php?id=$id"
            title = titulo
            if (!portada.isNullOrBlank()) {
                thumbnail_url = if (portada.startsWith("http")) portada else "$baseUrl/${portada.trimStart('/')}"
            }
        }
    }
}
