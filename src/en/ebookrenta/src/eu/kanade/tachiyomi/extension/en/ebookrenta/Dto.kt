package eu.kanade.tachiyomi.extension.en.ebookrenta

import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jsoup.parser.Parser
import kotlin.time.Instant

@Serializable
class Item(
    @SerialName("prd_ser") private val prdSer: String,
    @SerialName("prd_name") private val prdName: String,
    @SerialName("date_s") private val dateS: String,
    @SerialName("prd_rental") private val prdRental: Int,
    @SerialName("prd_rental_free") private val prdRentalFree: Int,
    @SerialName("prd_rental_free_buy") private val prdRentalFreeBuy: Int,
    @SerialName("prd_rental_all_free") private val prdRentalAllFree: Int,
    @SerialName("prd_rental_all_free_buy") private val prdRentalAllFreeBuy: Int,
    @SerialName("prd_rental_future") private val prdRentalFuture: Int,
    @SerialName("block_sample") private val blockSample: Int,
) {
    val isFuture: Boolean
        get() = prdRentalFuture == 1

    private val isFree: Boolean
        get() = prdRentalFree == 1 || prdRentalFreeBuy == 1 || prdRentalAllFree == 1 || prdRentalAllFreeBuy == 1

    // prd_rental: 1 = bought, 4 = currently rented
    val isLocked: Boolean
        get() = prdRental != 1 && prdRental != 4 && !isFree

    fun toSChapter() = SChapter.create().apply {
        val isPreview = isLocked && blockSample == 1
        url = prdSer
        name = when {
            !isLocked -> ""
            isPreview -> "🔒 (Preview) "
            else -> "🔒 "
        } + Parser.unescapeEntities(prdName, false)
        date_upload = Instant.tryParse(dateS)
        memo = buildJsonObject {
            val type = when {
                prdRental != 1 && isFree -> "free"
                isPreview -> "smpl"
                else -> "read"
            }
            put("type", type)
        }
    }
}

@Serializable
class FilterData(
    val genres: List<Facet>,
    val categories: List<Facet>,
    val keywords: List<Facet>,
    val deals: List<Facet>,
    val onSale: List<Facet>,
)

@Serializable
class FacetResponse(
    val response: List<Facet>,
)

@Serializable
class Facet(
    private val value: String,
    @SerialName("value_enc") private val valueEnc: String,
) {
    fun toPair() = value.replace('_', ' ') to valueEnc
}
