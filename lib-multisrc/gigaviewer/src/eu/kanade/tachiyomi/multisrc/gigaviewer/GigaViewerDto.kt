package eu.kanade.tachiyomi.multisrc.gigaviewer

import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

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
    fun toSChapter(isVolume: Boolean = false) = SChapter.create().apply {
        val volPrefix = if (isVolume) "(Volume) " else ""
        val prefix = when (status?.label) {
            "unpublished" -> "🔒 "
            "is_rentable", "is_purchasable", "is_rentable_and_subscribable" -> "💴 "
            else -> ""
        }
        name = prefix + volPrefix + title
        date_upload = Instant.tryParse(displayOpenAt)
        url = if (isVolume) "/volume/$readableProductId" else "/episode/$readableProductId"
    }
}

@Serializable
class GigaViewerPaginationReadableProductStatus(
    val label: String?, // is_free, is_rentable, is_purchasable, unpublished, is_rentable_and_subscribable
)
