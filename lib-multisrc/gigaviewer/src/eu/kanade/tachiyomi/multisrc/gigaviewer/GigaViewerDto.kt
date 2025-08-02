package eu.kanade.tachiyomi.multisrc.gigaviewer

import kotlinx.serialization.Serializable

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
data class GigaViewerEpisodesDto(
    val episodes: List<GigaViewerPaginationReadableProduct> = emptyList(),
)

@Serializable
data class GigaViewerPaginationReadableProduct(
    val description: String?,
    val display_open_at: String = "",
    val event_free_open_limit: String?,
    val free_term_start_at: String?,
    val open_limit: String?,
    val readable_product_id: String = "",
    val status: GigaViewerPaginationReadableProductStatus?,
    val thumbnail_alt: String = "",
    val thumbnail_uri: String = "",
    val title: String = "",
    val viewer_uri: String = "",
)

@Serializable
data class GigaViewerPaginationReadableProductStatus(
    val buy_price: Int?,
    val label: String?,
    val rental_end_at: String?,
    val rental_price: Int?,
    val rental_term: Int?,
    val private_reason_message: String?,
    val type: String?,
)
