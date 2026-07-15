package eu.kanade.tachiyomi.extension.ja.pixivcomic

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup

@Serializable
class ApiResponse<T>(
    val data: T,
)

@Serializable
class PopularResponse(
    val ranking: List<Ranking>,
)

@Serializable
class Ranking(
    val id: Int,
    private val title: String,
    @SerialName("main_image_url") private val mainImageUrl: String?,
) {
    fun toSManga() = SManga.create().apply {
        url = id.toString()
        title = this@Ranking.title
        thumbnail_url = mainImageUrl?.toHttpUrl()?.newBuilder()
            ?.removePathSegment(1)
            ?.removePathSegment(0)
            ?.build()
            ?.toString()
    }
}

@Serializable
class SeriesResponse(
    @SerialName("next_page_number") private val nextPageNumber: Int?,
    @SerialName("next_url") private val nextUrl: String?,
    @SerialName("official_works") val officialWorks: List<OfficialWork>,
) {
    fun hasNextPage() = nextPageNumber != null || nextUrl != null
}

@Serializable
class StoreSearchResponse(
    @SerialName("next_page_number") private val nextPageNumber: Int?,
    @SerialName("next_url") private val nextUrl: String?,
    val products: List<Product>,
) {
    fun hasNextPage() = nextPageNumber != null || nextUrl != null
}

@Serializable
class Product(
    private val key: String,
    private val title: String,
    private val explanation: String?,
    @SerialName("image_url") private val imageUrl: String?,
    @SerialName("author_name") private val authorName: String?,
    @SerialName("official_work_id") private val officialWorkId: Int?,
) {
    fun toSManga() = SManga.create().apply {
        url = officialWorkId?.toString() ?: key
        title = this@Product.title
        description = explanation?.let { Jsoup.parseBodyFragment(it).text() }
        author = authorName
        thumbnail_url = imageUrl?.toHttpUrl()?.newBuilder()
            ?.removePathSegment(1)
            ?.removePathSegment(0)
            ?.build()
            ?.toString()
        memo = buildJsonObject {
            if (officialWorkId == null) {
                put("store", "1")
            }
        }
    }
}

@Serializable
class OfficialWork(
    private val id: Int,
    private val name: String,
    private val author: String?,
    private val description: String?,
    private val categories: List<Genres>?,
    private val magazine: Magazine?,
    private val tags: List<Genres>?,
    @SerialName("store_product_key") val storeProductKey: String?,
    private val image: Image?,
) {
    fun toSManga() = SManga.create().apply {
        url = id.toString()
        title = name
        description = buildString {
            this@OfficialWork.description?.let { append(Jsoup.parseBodyFragment(it).text()) }

            magazine?.name?.takeIf { it.isNotEmpty() }?.let {
                append("\n\nMagazine: $it")
            }
        }
        author = this@OfficialWork.author
        genre = categories?.joinToString { it.name } + tags?.joinToString { it.name }
        thumbnail_url = image?.mainBig
    }
}

@Serializable
class Image(
    @SerialName("main_big") val mainBig: String?,
)

@Serializable
class Magazine(
    val name: String,
)

@Serializable
class Genres(
    val name: String,
)

@Serializable
class DetailsResponse(
    @SerialName("official_work") val officialWork: OfficialWork,
)

@Serializable
class ChapterResponse(
    val episodes: List<Episodes>,
)

@Serializable
class Episodes(
    val episode: Episode?,
)

@Serializable
class Episode(
    private val id: Int,
    @SerialName("numbering_title") private val numberingTitle: String,
    @SerialName("sub_title") private val subTitle: String?,
    @SerialName("read_start_at") private val readStartAt: Long?,
    private val state: String?,
) {
    val isLocked: Boolean
        get() = state != "readable"

    fun toSChapter() = SChapter.create().apply {
        val lock = if (isLocked) "🔒 " else ""
        val title = if (subTitle.isNullOrEmpty()) "" else ": $subTitle"
        url = id.toString()
        name = lock + numberingTitle + title
        date_upload = readStartAt ?: 0L
    }
}

@Serializable
class ProductResponse(
    val product: Product,
    val variants: List<Variant>,
)

@Serializable
class VolumeResponse(
    val variants: List<Variant>,
)

@Serializable
class Variant(
    private val sku: String,
    private val name: String,
    @SerialName("permit_start_on") private val permitStartOn: Long?,
    private val price: Price?,
    @SerialName("purchased_at") private val purchasedAt: Long?,
) {
    val isLocked: Boolean
        get() = purchasedAt == null && price?.value != 0

    fun toSChapter() = SChapter.create().apply {
        val lock = if (isLocked) "🔒 " else ""
        url = sku
        name = lock + this@Variant.name
        date_upload = permitStartOn ?: 0L
    }
}

@Serializable
class Price(
    val value: Int?,
)

@Serializable
class SaltResponse(
    val props: Props,
)

@Serializable
class Props(
    val pageProps: PageProps,
)

@Serializable
class PageProps(
    val salt: String,
)

@Serializable
class ViewerResponse(
    @SerialName("reading_episode") val readingEpisode: ReadingEpisode,
)

@Serializable
class ReadingEpisode(
    val pages: List<Page>?,
)

@Serializable
class Page(
    val url: String,
)

@Serializable
class CategoryResponse(
    val categories: List<Category>,
)

@Serializable
class Category(
    val name: String,
)
