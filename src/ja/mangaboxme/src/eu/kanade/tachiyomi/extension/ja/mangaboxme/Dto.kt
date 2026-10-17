package eu.kanade.tachiyomi.extension.ja.mangaboxme

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Suppress("unused")
@Serializable
class RpcRequest(
    private val jsonrpc: String,
    private val method: String,
    private val params: MangaParams,
)

@Suppress("unused")
@Serializable
class MangaParams(
    private val mangaId: String,
    private val withTags: Int,
)

@Serializable
class FeaturedResponse(
    val featured: Featured,
)

@Serializable
class Featured(
    val sections: List<Section>,
)

@Serializable
class Section(
    val items: List<SectionItem> = emptyList(),
)

@Serializable
class SectionItem(
    private val mangaID: Int,
    private val title: String,
) {
    fun toSManga(): SManga = SManga.create().apply {
        url = mangaID.toString()
        title = this@SectionItem.title
        thumbnail_url = mangaID.toThumbnail()
    }
}

@Serializable
class SearchResponse(
    val honshiMangas: List<HonshiManga>,
)

@Serializable
class HonshiManga(
    private val mangaID: Int,
    val title: String,
    val searchKeywords: List<String>,
) {
    fun toSManga(): SManga = SManga.create().apply {
        url = mangaID.toString()
        title = this@HonshiManga.title
        thumbnail_url = mangaID.toThumbnail()
    }
}

@Serializable
class DetailsResponse(
    val result: MangaDetails,
)

@Serializable
class MangaDetails(
    private val id: Int,
    private val title: String,
    private val description: String?,
    private val authors: List<Author>?,
    private val tags: Tags?,
    val episodes: List<Episode>,
) {
    fun toSManga(): SManga = SManga.create().apply {
        title = this@MangaDetails.title
        description = this@MangaDetails.description
        thumbnail_url = id.toThumbnail()
        author = authors?.joinToString { it.name }
        genre = tags?.let { it.genre.orEmpty() + it.subgenre.orEmpty() + it.original.orEmpty() }?.joinToString { it.name }
    }
}

@Serializable
class Author(
    val name: String,
)

@Serializable
class Tags(
    val genre: List<Tag>?,
    val subgenre: List<Tag>?,
    val original: List<Tag>?,
)

@Serializable
class Tag(
    val name: String,
)

@Serializable
class Episode(
    private val id: Int,
    private val volume: Float,
    private val displayVolume: String?,
    private val publishedDate: Long?,
    private val expiredDate: Long?,
    private val downloadableDate: Long,
    private val appearedDate: Long?,
    private val freeRentalStatus: FreeRentalStatus?,
    private val coinRentalStatus: CoinRentalStatus?,
    private val movieRentalStatus: MovieRentalStatus?,
) {
    private val isOpen: Boolean
        get() = publishedDate != null && expiredDate != null && System.currentTimeMillis() / 1000 in publishedDate..<expiredDate

    val isReleased: Boolean
        get() = downloadableDate <= System.currentTimeMillis() / 1000

    val isLocked: Boolean
        get() {
            val now = System.currentTimeMillis() / 1000
            val rentalExpiredDate = freeRentalStatus?.freeRentalExpiredDate ?: coinRentalStatus?.coinRentalExpiredDate
            return coinRentalStatus?.purchasedDate == null &&
                !isOpen &&
                (rentalExpiredDate == null || rentalExpiredDate < now)
        }

    fun toSChapter(mangaId: String): SChapter = SChapter.create().apply {
        val lock = if (isLocked) "🔒 " else ""
        url = id.toString()
        name = lock + (displayVolume ?: "第${volume.toString().removeSuffix(".0")}話")
        chapter_number = volume
        // Reprints backdate publishedDate, and unknown dates are filler before MangaBox's launch (2013-12) or ~2100
        date_upload = listOfNotNull(
            appearedDate,
            freeRentalStatus?.freeRentalStartedDate,
            coinRentalStatus?.coinRentalStartedDate,
            movieRentalStatus?.movieRentalStartedDate,
            publishedDate?.takeIf { isOpen },
        ).filter { it in 1385823600..<4070908800 }.minOrNull()?.times(1000) ?: 0L
        memo = buildJsonObject {
            put("mangaId", mangaId)
        }
    }
}

@Serializable
class FreeRentalStatus(
    val freeRentalStartedDate: Long?,
    val freeRentalExpiredDate: Long?,
)

@Serializable
class CoinRentalStatus(
    val purchasedDate: Long?,
    val coinRentalStartedDate: Long?,
    val coinRentalExpiredDate: Long?,
)

@Serializable
class MovieRentalStatus(
    val movieRentalStartedDate: Long?,
)

@Serializable
class ImagesResponse(
    val imageUrls: List<String>,
    val mask: Int,
)

private fun Int.toThumbnail() = "https://image-c.cdn.mangabox.me/image/upload/manga_episode_grid/manga_episode_grid_$this.png"
