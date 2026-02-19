package eu.kanade.tachiyomi.multisrc.gigaviewer

import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat

// Viewer
@Serializable
class GigaViewerEpisodeDto(
    val readableProduct: GigaViewerReadableProduct,
)

@Serializable
class GigaViewerReadableProduct(
    val pageStructure: GigaViewerPageStructure?,
)

@Serializable
class GigaViewerPageStructure(
    val pages: List<GigaViewerPage>,
    val choJuGiga: String,
)

@Serializable
class GigaViewerPage(
    val src: String?,
    val type: String?,
)

// Chapters
@Serializable
class GigaViewerPaginationReadableProduct(
    @SerialName("display_open_at") private val displayOpenAt: String?,
    @SerialName("readable_product_id") private val readableProductId: String,
    val status: GigaViewerPaginationReadableProductStatus?,
    private val title: String,
) {
    fun toSChapter(dateFormat: SimpleDateFormat) = SChapter.create().apply {
        val prefix = when (status?.label) {
            "unpublished" -> "🔒 "
            "is_rentable", "is_purchasable", "is_rentable_and_subscribable" -> "💴 "
            else -> ""
        }
        name = prefix + title
        date_upload = dateFormat.tryParse(displayOpenAt)
        url = "/episode/$readableProductId"
    }
}

@Serializable
class GigaViewerPaginationReadableProductStatus(
    val label: String?, // is_free, is_rentable, is_purchasable, unpublished
)
