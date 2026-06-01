package eu.kanade.tachiyomi.extension.ja.readerstore

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Serializable
class SearchResponse(
    val response: Response,
)

@Serializable
class Response(
    private val numFound: Int,
    private val start: Int,
    val docs: List<Doc>,
) {
    fun hasNextPage() = start + docs.size < numFound
}

@Serializable
class Doc(
    private val iid: String,
    @SerialName("first_thumbnail_s") private val firstThumbnailS: String?,
    @SerialName("thumbnail_sm") private val thumbnailSm: List<String>?,
    @SerialName("name_su") private val nameSu: String,
) {
    fun toSManga() = SManga.create().apply {
        url = iid
        title = nameSu
        thumbnail_url = (thumbnailSm?.firstOrNull() ?: firstThumbnailS)?.let { raw ->
            raw.substringBefore(",") + SIZES.firstOrNull { it in raw }
        }
    }

    companion object {
        private val SIZES = listOf("XLARGE.jpg", "LARGE.jpg", "MIDDLE.jpg", "SMALL.jpg")
    }
}

@Serializable
class MangaResponseItem(
    private val aid: String,
    private val detail: Detail,
    private val title: Title,
    private val authors: List<Author>?,
    private val floor: Floor?,
    private val price: Price?,
    private val browserView: BrowserView?,
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        title = this@MangaResponseItem.title.titleNm
        author = authors?.joinToString { it.authorNm }
        description = buildString {
            detail.titleExplanationLong?.let { append(Jsoup.parseBodyFragment(it).text()) }
            detail.magazineNm?.let { append("\n\nMagazine: $it") }
            detail.labelNm?.let { append("\n\nLabel: $it") }
            detail.publisherNm?.let { append("\n\nPublisher: $it") }
            if (detail.adultLevel == 1) append("\n\nRating: 18+")
        }
        genre = floor?.genres?.joinToString { it.genreNm }
        thumbnail_url = baseUrl + (detail.rsThumbnailLL ?: detail.rsThumbnailL ?: detail.rsThumbnailM ?: detail.rsThumbnailS)
    }

    val isLocked: Boolean
        get() = price?.priceIncludeTax != 0 && browserView?.isBrowseSample == false && detail.paidVersionAid == null

    val isPreview: Boolean
        get() = price?.priceIncludeTax != 0 && browserView?.isBrowseSample == true && detail.paidVersionAid == null

    fun toSChapter(): SChapter = SChapter.create().apply {
        val lock = if (isLocked) "🔒 " else ""
        val preview = if (isPreview) "🔒 (Preview) " else ""
        url = detail.paidVersionAid ?: if (isPreview) "$aid#1" else aid
        name = lock + preview + detail.contentsNm
        date_upload = dateFormat.tryParse(detail.originalPublicDt)
        chapter_number = title.titleIndexSeqNo?.toFloat() ?: -1f
    }
}

private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT).apply {
    timeZone = TimeZone.getTimeZone("Asia/Tokyo")
}

@Serializable
class Detail(
    val contentsNm: String,
    val magazineNm: String?,
    val labelNm: String?,
    val publisherNm: String?,
    val rsThumbnailLL: String?,
    val rsThumbnailL: String?,
    val rsThumbnailM: String?,
    val rsThumbnailS: String?,
    val adultLevel: Int?,
    val paidVersionAid: String?,
    val titleExplanationLong: String?,
    val originalPublicDt: String?,
)

@Serializable
class Title(
    val titleNm: String,
    val titleIndexSeqNo: Int?,
)

@Serializable
class Author(
    val authorNm: String,
)

@Serializable
class Floor(
    val genres: List<Genre>?,
)

@Serializable
class Genre(
    val genreNm: String,
)

@Serializable
class Price(
    val priceIncludeTax: Int?,
)

@Serializable
class BrowserView(
    val isBrowseSample: Boolean?,
)

@Serializable
class TokenResponse(
    val token: Token,
)

@Serializable
class Token(
    val authToken: String,
    val uuid: String,
    val browserContentsId: String,
)

@Serializable
class MetaResponse(
    val data: MetaData,
)

@Serializable
class MetaData(
    val type: String,
    val page: Page,
)

@Serializable
class Page(
    val all: Int?,
)

@Serializable
class ImageResponse(
    val data: Data,
)

@Serializable
class Data(
    val url: String,
    val meta: List<Meta>,
)

@Serializable
class Meta(
    val isCrypted: Boolean,
    val isScrambled: Boolean,
    val mimetype: String,
    val width: Int,
    val height: Int,
)
