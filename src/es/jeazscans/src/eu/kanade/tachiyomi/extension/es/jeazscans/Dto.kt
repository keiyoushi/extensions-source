package eu.kanade.tachiyomi.extension.es.jeazscans

import android.util.Base64
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

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
    @SerialName("price") val price: JsonElement? = null,
    @SerialName("payment_until") val paymentUntil: String? = null,
) {
    /**
     * Pure mapping of an API chapter record into [ChapterData], free of any
     * host-app runtime type. Returns `null` only for records whose `number`
     * cannot be parsed. Locked (paid) records are kept so they surface in the
     * chapter list, but they receive the non-readable [LOCKED_READER_URL] so
     * they can never be opened by accident.
     */
    fun toChapterData(slug: String, baseUrl: String): ChapterData? {
        val chapterNumber = number?.toFloatOrNull() ?: return null
        val baseName = title?.takeIf { it.isNotBlank() }
            ?: "Chapter ${chapterNumber.toString().removeSuffix(".0")}"
        return if (isLocked) {
            ChapterData(
                readerUrl = LOCKED_READER_URL,
                chapterNumber = chapterNumber,
                name = baseName,
                dateUpload = parseChapterDate(publishedAt),
                isLocked = true,
                priceCoins = (price as? JsonPrimitive)?.content?.toDoubleOrNull()?.toInt(),
                paymentUntilEpoch = parsePaymentUntil(paymentUntil),
            )
        } else {
            val chapterUrl = "$baseUrl/leer/$slug/capitulo-$number"
            ChapterData(
                readerUrl = chapterUrl.substringAfter(baseUrl),
                chapterNumber = chapterNumber,
                name = baseName,
                dateUpload = parseChapterDate(publishedAt),
            )
        }
    }

    /**
     * Materialize a host-app [SChapter], delegating the pure mapping to
     * [toChapterData]. Locked chapters use the decorated display name from
     * [ChapterData.displayName].
     */
    fun toSChapter(slug: String, baseUrl: String): SChapter? {
        val data = toChapterData(slug, baseUrl) ?: return null
        return SChapter.create().apply {
            url = data.readerUrl
            chapter_number = data.chapterNumber
            name = data.displayName()
            date_upload = data.dateUpload
        }
    }
}

/**
 * Lock symbol prepended to locked (paid) chapter names in the chapter list.
 */
internal const val LOCK_SYMBOL = "🔒"

/**
 * Non-readable placeholder used as the reader URL for locked (paid) chapters so
 * tapping them never opens a reader. Matches the `href="javascript:void(0)"` the
 * site emits for locked chapters.
 */
internal const val LOCKED_READER_URL = "javascript:void(0)"

/**
 * Pure, host-app-free representation of a parsed API chapter. Unit tests can
 * verify the mapping without touching `SChapter.create()`.
 */
class ChapterData(
    val readerUrl: String,
    val chapterNumber: Float,
    val name: String,
    val dateUpload: Long,
    val isLocked: Boolean = false,
    val priceCoins: Int? = null,
    val paymentUntilEpoch: Long? = null,
) {
    /**
     * Chapter-list display name. Unlocked chapters are returned verbatim.
     * Locked chapters are prefixed with [LOCK_SYMBOL], include the price in
     * coins when the API exposes it, and append a snapshot of the remaining
     * time until the chapter unlocks (`Gratis en 0d 11h 24m`) or `Gratis
     * disponible` once the deadline has passed.
     *
     * The countdown is a snapshot captured at chapter-list refresh time; the
     * extension cannot live-tick it like the website without a reload.
     */
    fun displayName(): String {
        if (!isLocked) return name
        val lockPrefix = "$LOCK_SYMBOL $name"
        val priceSuffix = priceCoins?.takeIf { it > 0 }?.let { " · $it monedas" }.orEmpty()
        val unlockSuffix = paymentUntilEpoch?.let { deadline ->
            val remainingMillis = deadline - System.currentTimeMillis()
            if (remainingMillis > 0L) " · Gratis en ${formatCountdown(remainingMillis)}" else " · Gratis disponible"
        }.orEmpty()
        return "$lockPrefix$priceSuffix$unlockSuffix"
    }
}

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
