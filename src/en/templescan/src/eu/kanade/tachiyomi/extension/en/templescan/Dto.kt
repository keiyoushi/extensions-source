package eu.kanade.tachiyomi.extension.en.templescan

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable

@Serializable
class TopSeriesResponse(
    val mensualRes: List<BrowseSeries>,
    val weekRes: List<BrowseSeries>,
    val dayRes: List<BrowseSeries>,
)

@Serializable
class BrowseSeries(
    val series_slug: String,
    val title: String,
    val thumbnail: String
) {
    fun toSManga() = SManga.create().apply {
        url = "/comic/$series_slug"
        title = this@BrowseSeries.title
        thumbnail_url = thumbnail
    }
}
