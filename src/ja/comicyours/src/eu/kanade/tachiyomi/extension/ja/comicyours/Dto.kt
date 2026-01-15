package eu.kanade.tachiyomi.extension.ja.comicyours

import eu.kanade.tachiyomi.multisrc.gigaviewer.DATE_PARSER_COMPLEX
import eu.kanade.tachiyomi.multisrc.gigaviewer.GigaViewer
import eu.kanade.tachiyomi.multisrc.gigaviewer.GigaViewerPaginationReadableProductStatus
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class Dto(
    @SerialName("display_open_at") private val displayOpenAt: String?,
    @SerialName("readable_product_id") private val readableProductId: String,
    private val status: GigaViewerPaginationReadableProductStatus?,
    private val title: String,
) {
    fun toSChapter(publisher: String) = SChapter.create().apply {
        name = title
        if (status?.label == "unpublished") {
            name = GigaViewer.LOCK + name
        } else if (status != null) {
            name = GigaViewer.YEN_BANKNOTE + name
        }

        date_upload = DATE_PARSER_COMPLEX.tryParse(displayOpenAt)
        scanlator = publisher
        url = "/episode/$readableProductId"
    }
}
