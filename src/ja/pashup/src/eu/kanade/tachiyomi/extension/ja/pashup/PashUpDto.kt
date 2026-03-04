package eu.kanade.tachiyomi.extension.ja.pashup

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import java.text.SimpleDateFormat

@Serializable
class EntryResponse(
    @SerialName("TotalResults") val totalResults: Int,
    @SerialName("Contents") val contents: List<Content>,
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
) {
    fun toSManga(): SManga = SManga.create().apply {
        url = seriesId
        title = name
        thumbnail_url = images?.series
        author = writers?.joinToString { "${it.roleName}: ${it.name}" }
        description = explain?.let { Jsoup.parse(it).text() }
        genre = tags?.joinToString { it }
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
class ChapterResponse(
    @SerialName("Contents") val contents: List<ChapterContent>,
)

@Serializable
class ChapterContent(
    @SerialName("Product") val product: Product,
    @SerialName("SeriesID") val seriesId: String,
    @SerialName("ProductMinMax") val productMinMax: Map<String, MinMax>?,
)

@Serializable
class MinMax(
    @SerialName("Min") val min: Product?,
    @SerialName("Max") val max: Product?,
)

@Serializable
class Product(
    @SerialName("ID") val id: String,
    @SerialName("Name") val name: String,
    @SerialName("StartDate") val startDate: String?,
    @SerialName("DownloadURL") val downloadUrl: String,
    @SerialName("SalesUnit") val salesUnit: String?,
    @SerialName("EndDate") val endDate: String?,
) {
    fun toSChapter(dateFormat: SimpleDateFormat, seriesId: String): SChapter = SChapter.create().apply {
        val isLocked = downloadUrl.contains("/pageapi/download")
        name = if (isLocked) "🔒 ${this@Product.name}" else this@Product.name
        url = "$seriesId#$id"
        date_upload = dateFormat.tryParse(startDate)
    }
}
