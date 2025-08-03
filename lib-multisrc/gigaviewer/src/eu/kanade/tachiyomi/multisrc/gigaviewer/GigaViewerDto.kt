package eu.kanade.tachiyomi.multisrc.gigaviewer

import eu.kanade.tachiyomi.multisrc.gigaviewer.GigaViewer.Companion.CHAPTER_LIST_LOCKED
import eu.kanade.tachiyomi.multisrc.gigaviewer.GigaViewer.Companion.CHAPTER_LIST_PAID
import eu.kanade.tachiyomi.multisrc.gigaviewer.GigaViewer.Companion.LOCK
import eu.kanade.tachiyomi.multisrc.gigaviewer.GigaViewer.Companion.YEN_BANKNOTE
import eu.kanade.tachiyomi.source.model.SChapter
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale

@Serializable
data class GigaViewerEpisodeDto(
    val readableProduct: GigaViewerReadableProduct,
)

@Serializable
data class GigaViewerReadableProduct(
    val pageStructure: GigaViewerPageStructure,
)

@Serializable
data class GigaViewerPageStructure(
    val pages: List<GigaViewerPage> = emptyList(),
)

@Serializable
data class GigaViewerPage(
    val height: Int = 0,
    val src: String = "",
    val type: String = "",
    val width: Int = 0,
)

@Serializable
class GigaViewerPaginationReadableProduct(
    val description: String?,
    val display_open_at: String?,
    val event_free_open_limit: String?,
    val free_term_start_at: String?,
    val open_limit: String?,
    val readable_product_id: String = "",
    val status: GigaViewerPaginationReadableProductStatus?,
    val thumbnail_alt: String = "",
    val thumbnail_uri: String = "",
    val title: String = "",
    val viewer_uri: String = "",
) {
    fun toSChapter(chapterListMode: Int, publisher: String) = SChapter.create().apply {
        name = title
        if (chapterListMode == CHAPTER_LIST_PAID && status?.label != IS_FREE) {
            name = YEN_BANKNOTE + name
        } else if (chapterListMode == CHAPTER_LIST_LOCKED && status?.label == UNPUBLISHED) {
            name = LOCK + name
        }
        date_upload = display_open_at?.toDate() ?: 0L
        scanlator = publisher
        url = "/episode/$readable_product_id"
    }

    companion object {
        // chapter status labels
        private const val IS_FREE = "is_free"
        private const val IS_RENTABLE = "is_rentable"
        private const val IS_PURCHASABLE = "is_purchasable"
        private const val UNPUBLISHED = "unpublished"
    }
}

@Serializable
class GigaViewerPaginationReadableProductStatus(
    val buy_price: Int?,
    val label: String?,
    val rental_end_at: String?,
    val rental_price: Int?,
    val rental_term: Int?,
    val private_reason_message: String?,
    val type: String?,
)

val DATE_PARSER_SIMPLE = SimpleDateFormat("yyyy/MM/dd", Locale.ENGLISH)
val DATE_PARSER_COMPLEX = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ENGLISH)

private fun String.toDate(): Long {
    return runCatching { DATE_PARSER_COMPLEX.parse(this)?.time }.getOrNull() ?: 0L
}
