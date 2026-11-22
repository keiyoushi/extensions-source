package eu.kanade.tachiyomi.extension.zh.bilibilimanga

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.tryParseDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import org.jsoup.Jsoup
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Serializable
class ResponseDto(
    val code: Int,
    val msg: String = "",
    val data: JsonElement? = null,
    val bytesData: String? = null,
)

@Serializable
class ClassPageItemDto(
    @SerialName("season_id") private val seasonId: Int,
    private val title: String,
    @SerialName("vertical_cover") private val verticalCover: String,
    private val author: List<String>,
    private val styles: List<String>,
    @SerialName("is_finish") private val isFinish: Int,
) {
    fun toSManga() = SManga.create().apply {
        url = "/detail/mc$seasonId"
        title = this@ClassPageItemDto.title
        thumbnail_url = verticalCover + COVER_SIZE
        author = this@ClassPageItemDto.author.joinToString().ifEmpty { null }
        genre = styles.joinToString().ifEmpty { null }
        status = status(isFinish)
    }
}

@Serializable
class SearchDto(
    val list: List<SearchItemDto>,
    @SerialName("total_num") val totalNum: Int,
)

@Serializable
class SearchItemDto(
    private val id: Int,
    private val title: String,
    @SerialName("real_title") private val realTitle: String? = null,
    @SerialName("vertical_cover") private val verticalCover: String,
    @SerialName("author_name") private val authorName: List<String>,
    private val styles: List<String>,
    @SerialName("is_finish") private val isFinish: Int,
) {
    fun toSManga() = SManga.create().apply {
        url = "/detail/mc$id"
        title = realTitle?.takeIf { it.isNotBlank() } ?: Jsoup.parse(this@SearchItemDto.title).text()
        thumbnail_url = verticalCover + COVER_SIZE
        author = authorName.joinToString().ifEmpty { null }
        genre = styles.joinToString().ifEmpty { null }
        status = status(isFinish)
    }
}

@Serializable
class ComicDto(
    val id: Int,
    private val title: String,
    @SerialName("vertical_cover") private val verticalCover: String,
    @SerialName("author_name") private val authorName: List<String>,
    private val styles: List<String>,
    @SerialName("story_elems") private val storyElems: List<NamedDto>,
    @SerialName("is_finish") private val isFinish: Int,
    @SerialName("classic_lines") private val classicLines: String,
    private val introduction: String,
    @SerialName("renewal_time") private val renewalTime: String,
    @SerialName("comic_alias") private val comicAlias: List<String>,
    @SerialName("series_info") private val seriesInfo: SeriesInfoDto,
    @SerialName("ep_list") val episodes: List<EpisodeDto>,
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        url = "/detail/mc$id"
        title = this@ComicDto.title
        thumbnail_url = verticalCover + COVER_SIZE
        author = authorName.joinToString().ifEmpty { null }
        genre = (styles + storyElems.map { it.name }).distinct().joinToString().ifEmpty { null }
        status = status(isFinish)
        description = MarkdownDescription.Builder()
            .info(renewalTime.takeIf { isFinish != 1 })
            .description(introduction)
            .description(classicLines)
            .altTitles(comicAlias, "别名")
            .links(seriesInfo.comics.filter { it.comicId != id }.map { it.title to "$baseUrl/detail/mc${it.comicId}" }, "系列作品")
            .build()
    }
}

@Serializable
class NamedDto(val name: String)

@Serializable
class SeriesInfoDto(val comics: List<SeriesComicDto>)

@Serializable
class SeriesComicDto(
    @SerialName("comic_id") val comicId: Int,
    val title: String,
)

@Serializable
class EpisodeDto(
    private val id: Int,
    @SerialName("short_title") private val shortTitle: String,
    private val title: String,
    @SerialName("pub_time") private val pubTime: String,
    @SerialName("is_locked") private val isLocked: Boolean,
    @SerialName("is_in_free") private val isInFree: Boolean,
) {
    val isPaywalled get() = isLocked && !isInFree

    fun toSChapter(comicId: Int) = SChapter.create().apply {
        url = "/mc$comicId/$id"
        val short = shortTitle.trim()
        val episodeTitle = title.trim()
        val label = if (NUMBERED.matches(short)) "第${short}话" else short
        name = buildString {
            if (isPaywalled) append("🔒 ")
            when {
                episodeTitle.isEmpty() || episodeTitle == short -> append(label)
                episodeTitle.startsWith(label) -> append(episodeTitle)
                else -> append(label).append(" ").append(episodeTitle)
            }
        }
        date_upload = dateFormat.tryParseDateTime(pubTime, ZONE)
    }
}

@Serializable
class ImageIndexDto(val images: List<ImageDto>)

@Serializable
class ImageDto(val path: String)

@Serializable
class ImageTokenDto(@SerialName("complete_url") val completeUrl: String)

@Serializable
class LabelsDto(
    val styles: List<LabelDto>,
    val areas: List<LabelDto>,
    val status: List<LabelDto>,
    val orders: List<LabelDto>,
    val prices: List<LabelDto>,
    val special: List<LabelDto>,
)

@Serializable
class LabelDto(val id: Int, val name: String)

@Serializable
class BuvidDto(val data: BuvidDataDto)

@Serializable
class BuvidDataDto(
    @SerialName("b_3") val buvid3: String,
    @SerialName("b_4") val buvid4: String,
)

@Serializable
class ComicIdBody(@SerialName("comic_id") val comicId: Int)

@Serializable
class ImageIndexBody(
    @SerialName("ep_id") val epId: Int,
    val m2: String,
)

@Serializable
class ImageTokenBody(
    val urls: String,
    val m1: String,
)

@Serializable
class SearchBody(
    @SerialName("key_word") val keyWord: String,
    @SerialName("page_num") val pageNum: Int,
    @SerialName("page_size") val pageSize: Int,
)

@Serializable
class ClassPageBody(
    @SerialName("style_id") val styleId: Int,
    @SerialName("area_id") val areaId: Int,
    @SerialName("is_finish") val isFinish: Int,
    val order: Int,
    @SerialName("special_tag") val specialTag: Int,
    @SerialName("page_num") val pageNum: Int,
    @SerialName("page_size") val pageSize: Int,
    @SerialName("is_free") val isFree: Int,
    val m2: String,
)

// Short titles the site's episode list shows as 第N话: ASCII or fullwidth digits only.
private val NUMBERED = Regex("""[0-9]+|[０-９]+""")

// The cover size that fits the app's grid and details screen; the plain url is the full-size original.
private const val COVER_SIZE = "@450w.webp"

private fun status(isFinish: Int) = if (isFinish == 1) SManga.COMPLETED else SManga.ONGOING

private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
private val ZONE = ZoneId.of("Asia/Shanghai")
