package eu.kanade.tachiyomi.extension.ja.pashup

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jsoup.Jsoup
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.time.Duration.Companion.days

@Serializable
class ContentResponse(
    @SerialName("TotalResults") val totalResults: Int,
    @SerialName("Contents") val contents: List<Content> = emptyList(),
)

@Serializable
class Content(
    @SerialName("SeriesID") private val seriesId: String,
    @SerialName("Name") private val name: String,
    @SerialName("Images") private val images: Images?,
    @SerialName("Category") val category: String, // 2 = mangas, 1/3 = novels/magazines
    @SerialName("Writers") private val writers: List<Writer>?,
    @SerialName("Explain") private val explain: String?,
    @SerialName("Tags") private val tags: List<String>?,
    @SerialName("Product") val product: Product?,
) {
    fun toSManga(): SManga = SManga.create().apply {
        url = seriesId
        title = name
        thumbnail_url = images?.series
        author = writers?.joinToString { "${it.roleName}: ${it.name}" }
        description = explain?.let { Jsoup.parseBodyFragment(it).text() }
        genre = tags?.joinToString()
        tags?.let { status = if (it.contains("完結")) SManga.COMPLETED else SManga.ONGOING }
    }
}

@Serializable
class Images(
    @SerialName("Series") val series: String?,
)

@Serializable
class Writer(
    val name: String,
    @SerialName("role_name") val roleName: String,
)

@Serializable
class Product(
    @SerialName("ID") val id: String,
    @SerialName("Name") private val name: String,
    @SerialName("StartDate") private val startDate: String?,
    @SerialName("EndDate") private val endDate: String?,
    @SerialName("DownloadURL") val downloadUrl: String,
    @SerialName("SalesUnit") val salesUnit: String?,
) {
    val isLocked: Boolean
        get() = downloadUrl.contains("/pageapi/download")

    val isAvailable: Boolean
        get() {
            val endTime = dateFormat.tryParseDate(endDate)
            return endTime == 0L || endTime + 1.days.inWholeMilliseconds > System.currentTimeMillis()
        }

    fun toSChapter(seriesId: String): SChapter = SChapter.create().apply {
        url = id
        name = if (isLocked) "🔒 ${this@Product.name}" else this@Product.name
        date_upload = dateFormat.tryParseDate(startDate)
        memo = buildJsonObject {
            put("seriesId", seriesId)
            if (!isLocked) put("viewerUrl", downloadUrl)
        }
    }
}

private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.of("Asia/Tokyo"))
