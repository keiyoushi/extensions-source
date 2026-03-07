package eu.kanade.tachiyomi.extension.ja.linemanga

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.text.SimpleDateFormat
import java.util.Locale

@Serializable
class EntryResponse(
    val result: Result,
)

@Serializable
class Result(
    @JsonNames("rows") val items: List<Item>,
    val pager: Pager?,
)

@Serializable
class Item(
    private val thumbnail: String?,
    @SerialName("is_light_novel") val isLightNovel: Boolean?,
    private val id: String,
    private val name: String,
) {
    fun toSManga(): SManga = SManga.create().apply {
        url = id
        title = name
        thumbnail_url = thumbnail?.toHttpUrl()?.newBuilder()?.removePathSegment(1)?.build().toString()
    }
}

@Serializable
class Pager(
    val hasNext: Boolean?,
)

@Serializable
class EntryDetails(
    val result: ResultDetails,
)

@Serializable
class ResultDetails(
    val product: Product,
    val rows: List<Rows>,
)

@Serializable
class Product(
    @SerialName("publisher_name") private val publisherName: String?,
    @SerialName("series_name") private val seriesName: String?,
    @SerialName("periodic_description") private val periodicDescription: String?,
    private val authors: List<Author>?,
    @SerialName("is_restricted18") private val isRestricted18: Boolean?,
    private val thumbnail: String?,
    @SerialName("genre_name") private val genreName: String?,
    private val caption: String?,
    private val name: String,
    private val explanation: String?,
) {
    fun toSManga(): SManga = SManga.create().apply {
        title = seriesName ?: name
        thumbnail_url = thumbnail?.toHttpUrl()?.newBuilder()?.removePathSegment(1)?.build().toString()
        author = this@Product.authors?.joinToString { it.name }
        description = buildString {
            if (!caption.isNullOrBlank()) {
                append("$caption\n\n")
            }

            append(explanation)

            if (!periodicDescription.isNullOrBlank()) {
                append("\n\n$periodicDescription")
            }
            if (!publisherName.isNullOrBlank()) {
                append("\n\nPublisher: $publisherName")
            }
            if (isRestricted18 == true) {
                append("\n\n+18")
            }
        }
        genre = genreName
    }
}

@Serializable
class Author(
    val name: String,
)

@Serializable
class Rows(
    private val name: String,
    @SerialName("selling_buy_price") private val sellingBuyPrice: Int?,
    private val id: String,
    @SerialName("permit_start") private val permitStart: String?,
    @SerialName("fin_of_purchase") private val finOfPurchase: Int?,
    @SerialName("series_name") private val seriesName: String?,
    private val volume: Int,
    @SerialName("expired_on") private val expiredOn: Long?,
) {
    val isLocked: Boolean
        get() {
            if (sellingBuyPrice == null) return finOfPurchase != 1
            if (expiredOn != null) return false
            return sellingBuyPrice > 0
        }

    fun toSChapter(): SChapter = SChapter.create().apply {
        val lock = if (isLocked) "🔒 " else ""
        val chapterName = if (seriesName != null) this@Rows.name.replace(seriesName, "").trim() else this@Rows.name
        url = id
        name = lock + chapterName
        chapter_number = volume.toFloat()
        date_upload = dateFormat.tryParse(permitStart)
    }
}

private val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.ROOT)
