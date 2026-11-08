package eu.kanade.tachiyomi.extension.all.dlsite

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.tryParseDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Serializable
class SearchDto(
    @SerialName("search_result") val searchResult: String,
    @SerialName("page_info") val pageInfo: PageInfoDto,
)

@Serializable
class PageInfoDto(
    val count: Int,
    @SerialName("last_indice") val lastIndice: Int,
)

@Serializable
class ProductDto(
    val workno: String,
    @SerialName("site_id") private val siteId: String,
    @SerialName("work_type") val workType: String,
    @SerialName("work_name") private val workName: String,
    @SerialName("work_type_string") private val workTypeString: String? = null,
    @SerialName("maker_name") private val makerName: String? = null,
    @SerialName("series_name") private val seriesName: String? = null,
    @SerialName("page_number") private val pageNumber: JsonElement? = null,
    @SerialName("regist_date") private val registDate: String? = null,
    @SerialName("intro_s") private val intro: String? = null,
    @SerialName("rate_average_star") private val rateAverageStar: Int = 0,
    @SerialName("rate_count_detail") private val rateCountDetail: JsonElement? = null,
    @SerialName("image_main") private val imageMain: ImageDto? = null,
    // An object of credits by role, or an empty array when the work has none
    private val creaters: JsonElement? = null,
    // Credits of the books (BJ) works
    private val author: List<AuthorDto>? = null,
    private val genres: List<GenreDto> = emptyList(),
) {
    val url get() = "/$siteId/work/=/product_id/$workno.html"

    private fun credits(role: String) = ((creaters as? JsonObject)?.get(role) as? JsonArray).orEmpty()
        .mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull }

    fun toSManga() = SManga.create().apply {
        url = this@ProductDto.url
        title = workName
        thumbnail_url = imageMain?.url?.let { if (it.startsWith("//")) "https:$it" else it }
        author = (credits("created_by") + credits("scenario_by") + this@ProductDto.author.orEmpty().map { it.name })
            .distinct()
            .joinToString()
            .ifEmpty { makerName }
        artist = credits("illust_by").joinToString().ifEmpty { null }
        genre = (listOfNotNull(workTypeString) + genres.map { it.name }).joinToString()
        val votes = (rateCountDetail as? JsonObject)?.values?.sumOf { it.jsonPrimitive.contentOrNull?.toIntOrNull() ?: 0 } ?: 0
        description = MarkdownDescription.Builder()
            .rating(if (votes > 0) rateAverageStar / 10.0 else null, max = 5, votes = votes)
            .description(intro)
            // Doujin works (RJ) have a circle, books (BJ) a publisher
            .field(if (workno.startsWith("BJ")) "Publisher" else "Circle", makerName)
            .field("Series", seriesName)
            .field("Pages", pageNumber?.jsonPrimitive?.contentOrNull)
            .build()
        status = SManga.COMPLETED
        initialized = true
    }

    fun toSChapter() = SChapter.create().apply {
        url = this@ProductDto.url
        name = "Chapter"
        chapter_number = 1f
        date_upload = DATE_FORMAT.tryParseDateTime(registDate)
    }
}

@Serializable
class ImageDto(val url: String? = null)

@Serializable
class AuthorDto(@SerialName("author_name") val name: String)

@Serializable
class GenreDto(val name: String)

private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT).withZone(ZoneId.of("Asia/Tokyo"))
