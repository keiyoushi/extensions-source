package eu.kanade.tachiyomi.multisrc.uzaymanga

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class ItemListLd(
    @SerialName("@type") private val type: String? = null,
    private val name: String? = null,
    val itemListElement: List<ListItemLd>? = null,
) {
    internal val listName get() = name
}

@Serializable
class ListItemLd(
    val item: BookSeriesLd? = null,
)

@Serializable
class BookSeriesLd(
    @SerialName("@type") private val type: String? = null,
    private val name: String? = null,
    private val url: String? = null,
    private val image: String? = null,
) {
    internal val mangaName get() = name
    internal val mangaUrl get() = url
    internal val mangaImage get() = image

    val hasRequiredFields: Boolean get() = name != null && url != null
    val isBookSeries: Boolean get() = type == "BookSeries"

    fun toSManga(thumbnailUrl: String?) = SManga.create().apply {
        title = name!!
        this.thumbnail_url = thumbnailUrl
    }
}
