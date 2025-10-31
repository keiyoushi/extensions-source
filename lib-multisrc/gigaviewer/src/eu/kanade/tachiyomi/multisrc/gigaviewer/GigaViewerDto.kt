package eu.kanade.tachiyomi.multisrc.gigaviewer

import eu.kanade.tachiyomi.multisrc.gigaviewer.GigaViewer.Companion.CHAPTER_LIST_LOCKED
import eu.kanade.tachiyomi.multisrc.gigaviewer.GigaViewer.Companion.CHAPTER_LIST_PAID
import eu.kanade.tachiyomi.multisrc.gigaviewer.GigaViewer.Companion.LOCK
import eu.kanade.tachiyomi.multisrc.gigaviewer.GigaViewer.Companion.YEN_BANKNOTE
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.utils.tryParse
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
    val choJuGiga: String,
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
    private val display_open_at: String?,
    private val readable_product_id: String = "",
    private val status: GigaViewerPaginationReadableProductStatus?,
    private val title: String = "",
) {
    fun toSChapter(chapterListMode: Int, publisher: String) = SChapter.create().apply {
        name = title
        if (chapterListMode == CHAPTER_LIST_PAID && status?.label != "is_free") {
            name = YEN_BANKNOTE + name
        } else if (chapterListMode == CHAPTER_LIST_LOCKED && status?.label == "unpublished") {
            name = LOCK + name
        }
        date_upload = DATE_PARSER_COMPLEX.tryParse(display_open_at)
        scanlator = publisher
        url = "/episode/$readable_product_id"
    }
}

@Serializable
class GigaViewerPaginationReadableProductStatus(
    val label: String?, // is_free, is_rentable, is_purchasable, unpublished
)

val DATE_PARSER_SIMPLE = SimpleDateFormat("yyyy/MM/dd", Locale.ENGLISH)
val DATE_PARSER_COMPLEX = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ENGLISH)
