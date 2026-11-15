package eu.kanade.tachiyomi.extension.ja.hakusensha

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
class RankingResponse(
    val ranking: List<Ranking>,
)

@Serializable
class Ranking(
    val product: Group,
)

@Serializable
class GroupsResponse(
    @JsonNames("products") val groups: List<Group>,
)

@Serializable
class Group(
    @SerialName("product_group_name") private val productGroupName: String,
    @SerialName("product_group_title") @JsonNames("product_search_title") private val productGroupTitle: String,
    @SerialName("cover_image") private val coverImage: String?,
    private val type: String,
) {
    fun toSManga() = SManga.create().apply {
        url = productGroupName
        title = productGroupTitle
        thumbnail_url = coverImage?.toThumbnail()
        memo = buildJsonObject {
            put("type", type)
        }
    }
}

@Serializable
class ProductsResponse(
    val products: List<Product>,
) {
    fun toSManga() = SManga.create().apply {
        val sorted = products.sortedBy { it.productOrder }
        val first = sorted.first()
        title = first.productGroupTitle
        author = first.author?.joinToString { it.authorName }
        description = first.introduction
        genre = first.category?.joinToString()
        thumbnail_url = sorted.last().coverImage?.toThumbnail()
        memo = buildJsonObject {
            put("type", first.type)
        }
    }
}

@Serializable
class Product(
    @SerialName("product_name") val productName: String,
    @SerialName("product_group_title") val productGroupTitle: String,
    @SerialName("product_order") val productOrder: Int?,
    @SerialName("product_order_title") private val productOrderTitle: String,
    @SerialName("cover_image") val coverImage: String?,
    val type: String,
    val author: List<Author>?,
    val introduction: String?,
    val category: List<String>?,
    @SerialName("release_at") val releaseAt: Long?,
    @SerialName("is_free_streaming") private val isFreeStreaming: Boolean?,
) {
    fun isLocked(purchased: Set<String>) = isFreeStreaming != true && productName !in purchased

    fun toSChapter(purchased: Set<String>) = SChapter.create().apply {
        val lock = if (isLocked(purchased)) "🔒 " else ""
        url = productName
        name = lock + productOrderTitle
        date_upload = releaseAt ?: 0L
        chapter_number = productOrder?.toFloat() ?: -1f
        memo = buildJsonObject {
            val access = when {
                productName in purchased -> "bookshelf"
                isFreeStreaming == true -> "free"
                else -> "locked"
            }
            put("type", type)
            put("access", access)
        }
    }
}

@Serializable
class Author(
    @SerialName("author_name") val authorName: String,
)

@Serializable
class PurchasedResponse(
    @SerialName("purchased_list") val purchasedList: List<String>,
)

@Serializable
class AuthToken(
    val token: String,
    val expire: Long,
)

@Serializable
class UserAuth(
    val userId: String,
    val token: String,
    val expire: Long,
)

@Serializable
class KeyResponse(
    val bs: Viewer,
)

@Serializable
class Viewer(
    val url: String,
)

private fun String.toThumbnail() = "https://assets.hakusensha-e.net/$this"
