package eu.kanade.tachiyomi.extension.zh.bookwalkertw

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.tryParseDate
import keiyoushi.utils.tryParseDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jsoup.Jsoup
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Serializable
class ProductPage(
    val props: ProductProps,
)

@Serializable
class ProductProps(
    val productData: Product,
)

@Serializable
class Product(
    @SerialName("product_name") private val productName: String,
    @SerialName("product_big_image") private val bigImage: String?,
    @SerialName("is_preview_product") private val isPreview: Boolean,
    @SerialName("is_owned_product") private val isOwned: Boolean,
    @SerialName("product_series_id") val seriesId: Int,
    @SerialName("product_series_name") private val seriesName: String?,
    @SerialName("product_series_list") val volumes: List<Volume>,
    @SerialName("type_tag") private val tags: List<Tag>,
    private val publisher: Publisher?,
    @SerialName("product_detail") private val detail: Detail?,
    @SerialName("product_detail_info") private val info: Info?,
) {
    val isSeries get() = seriesId > 0

    fun toSManga(url: String) = SManga.create().apply {
        this.url = url
        title = if (isSeries) seriesName!! else productName
        thumbnail_url = bigImage
        author = listOfNotNull(info?.author, info?.originalAuthor, info?.screenwriter)
            .filter(String::isNotBlank).joinToString().ifEmpty { null }
        artist = listOfNotNull(info?.mangaArtist, info?.illustrator)
            .filter(String::isNotBlank).joinToString().ifEmpty { null }
        genre = tags.joinToString { it.name }.ifEmpty { null }
        // The headline is often just the title
        val headline = detail?.name?.takeUnless { it == productName || it == seriesName }
        description = MarkdownDescription.Builder()
            .description(headline?.let { Jsoup.parseBodyFragment(it).body() })
            .description(detail?.introduction?.let { Jsoup.parseBodyFragment(it).body() })
            .field("角色設定", info?.characterSetting)
            .field("機械設定", info?.mechanicalSetting)
            .field("攝影", info?.photographer)
            .field("監製", info?.producer)
            .field("譯者", info?.translator)
            .field("出版社", publisher?.text)
            .build()
        if (!isSeries) {
            status = SManga.COMPLETED
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        }
    }

    fun toSChapter(id: Int) = bookChapter(id, productName, null, isOwned, isPreview).apply {
        date_upload = SELL_DATE_FORMAT.tryParseDate(info?.sellDateStart, TAIPEI)
    }
}

@Serializable
class Volume(
    val id: Int,
    private val name: String,
    @SerialName("is_owned_series_product") private val isOwned: Boolean,
    @SerialName("is_preview_series_product") private val isPreview: Boolean,
) {
    fun toSChapter(seriesName: String?) = bookChapter(id, name, seriesName, isOwned, isPreview)
}

@Serializable
class Tag(
    val name: String,
)

@Serializable
class Publisher(
    val text: String,
)

@Serializable
class Detail(
    val name: String?,
    val introduction: String?,
)

@Serializable
class Info(
    val author: String?,
    @SerialName("manga_artist") val mangaArtist: String?,
    @SerialName("original_author") val originalAuthor: String?,
    val screenwriter: String?,
    val illustrator: String?,
    val photographer: String?,
    @SerialName("character_setting") val characterSetting: String?,
    @SerialName("mechanical_setting") val mechanicalSetting: String?,
    val producer: String?,
    val translator: String?,
    @SerialName("sell_date_start") val sellDateStart: String?,
)

@Serializable
class EpisodePage(
    val data: List<Episode>,
    @SerialName("last_page") val lastPage: Int,
)

@Serializable
class Episode(
    @SerialName("product_id") private val id: Int,
    @SerialName("product_name") private val name: String,
    @SerialName("series_name") private val seriesName: String?,
    private val episode: String?,
    @SerialName("product_sell_date_start") private val sellDate: String?,
    @SerialName("is_preview") private val isPreview: Int,
    @SerialName("is_owned") private val isOwned: Boolean,
) {
    fun toSChapter() = bookChapter(id, name, seriesName, isOwned, isPreview == 1).apply {
        episode?.toFloatOrNull()?.let { chapter_number = it }
        date_upload = DATE_TIME_FORMAT.tryParseDateTime(sellDate, TAIPEI)
    }
}

private val TAIPEI = ZoneId.of("Asia/Taipei")
private val DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
private val SELL_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy年M月d日")

// The site's own viewer for each book: the full book once owned, otherwise its preview
private fun bookChapter(id: Int, name: String, seriesName: String?, isOwned: Boolean, isPreview: Boolean) = SChapter.create().apply {
    url = "/product/$id"
    val shortName = seriesName?.takeIf(String::isNotEmpty)?.let { name.removePrefix(it).trim() }?.ifEmpty { null } ?: name
    this.name = when {
        isOwned -> shortName
        isPreview -> "🔒 (Preview) $shortName"
        else -> "🔒 $shortName"
    }
    val viewer = when {
        isOwned -> "read"
        isPreview -> "trial"
        else -> null
    }
    if (viewer != null) memo = buildJsonObject { put("viewer", viewer) }
}
