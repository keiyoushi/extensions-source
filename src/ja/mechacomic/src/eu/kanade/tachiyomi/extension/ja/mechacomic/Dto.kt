package eu.kanade.tachiyomi.extension.ja.mechacomic

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class RankingResponse(
    val pagination: Pagination,
    @SerialName("ranking_books") val rankingBooks: List<RankingBook>,
)

@Serializable
class Pagination(
    @SerialName("current_page") private val currentPage: Int,
    @SerialName("per_page") private val perPage: Int,
    @SerialName("total_entries") private val totalEntries: Int,
) {
    fun hasNextPage() = currentPage * perPage < totalEntries
}

@Serializable
class RankingBook(
    private val id: Int,
    private val name: String,
    @SerialName("jacket_image_path") private val jacketImagePath: String?,
) {
    fun toSManga(cdnUrl: String) = SManga.create().apply {
        url = id.toString()
        title = name
        thumbnail_url = jacketImagePath?.let { "$cdnUrl/images/$it" }
    }
}

@Serializable
class RecentResponse(
    val books: List<RecentBook>,
    @SerialName("has_next_page") val hasNextPage: Boolean,
)

@Serializable
class RecentBook(
    private val id: Int,
    private val title: String,
    @SerialName("jacket_image_url") private val jacketImageUrl: String?,
) {
    fun toSManga(cdnUrl: String) = SManga.create().apply {
        url = id.toString()
        title = this@RecentBook.title
        thumbnail_url = jacketImageUrl?.let { "$cdnUrl/$it" }
    }
}

@Serializable
class CryptoKey(
    val cryptokey: String,
)

@Serializable
class ContentData(
    private val pages: List<ContentPage>,
    private val images: Map<String, List<ImageData>>,
) {
    fun imagePaths(): List<String> = pages.mapNotNull { page ->
        page.image?.let { images[it]?.first()?.src }
    }
}

@Serializable
class ContentPage(
    val image: String?,
)

@Serializable
class ImageData(
    val src: String,
)
