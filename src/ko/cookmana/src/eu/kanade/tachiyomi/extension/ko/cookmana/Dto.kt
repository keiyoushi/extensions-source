package eu.kanade.tachiyomi.extension.ko.cookmana

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParseDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.Normalizer
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Serializable
class ListDto(
    @SerialName("last_page") val lastPage: Int,
    val data: List<ListItemDto>,
)

@Serializable
class ListItemDto(
    val id: String,
    val title: String,
    val src: String?,
    val tags: String?,
    val author: String?,
) {
    fun toSManga(): SManga = SManga.create().apply {
        url = "/episode/${this@ListItemDto.id}"
        title = this@ListItemDto.title
        thumbnail_url = this@ListItemDto.src.toAbsoluteUrl()
        author = this@ListItemDto.author
        genre = this@ListItemDto.tags.toGenres()
    }
}

@Serializable
class SearchDto(
    val data: SearchDataDto,
)

@Serializable
class SearchDataDto(
    val total: Int,
    val list: List<SearchItemDto>,
)

@Serializable
class SearchItemDto(
    val id: String,
    val title: String,
    val tags: String?,
    val author: String?,
) {
    fun toSManga(): SManga = SManga.create().apply {
        url = "/episode/${this@SearchItemDto.id}"
        title = this@SearchItemDto.title
        thumbnail_url = "$COVER_HOST/data/toon_category/${this@SearchItemDto.id}.webp"
        author = this@SearchItemDto.author
        genre = this@SearchItemDto.tags.toGenres()
    }
}

@Serializable
class EpisodeListDto(
    @SerialName("last_page") val lastPage: Int,
    val data: List<EpisodeItemDto>,
)

@Serializable
class EpisodeItemDto(
    val id: String,
    val title: String,
    val datetime: String?,
) {
    fun toSChapter(parentTitle: String): SChapter = SChapter.create().apply {
        url = "/detail/${this@EpisodeItemDto.id}"
        // The site prefixes chapter titles with the work name, sometimes in jamo: normalize to NFC before stripping.
        val normalized = Normalizer.normalize(this@EpisodeItemDto.title, Normalizer.Form.NFC)
        val parent = Normalizer.normalize(parentTitle, Normalizer.Form.NFC)
        name = normalized.removePrefix("$parent ").removePrefix(parent)
        date_upload = DATE_FORMAT.tryParseDateTime(this@EpisodeItemDto.datetime, SEOUL)
    }
}

@Serializable
class DetailDto(
    val data: DetailDataDto?,
)

@Serializable
class DetailDataDto(
    val folder3: String,
    val urls: String,
    val parentId: String?,
)

private const val COVER_HOST = "https://smallimage.11toon8.com"
private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
private val SEOUL = ZoneId.of("Asia/Seoul")

fun String?.toAbsoluteUrl(): String? = this?.let { if (it.startsWith("//")) "https:$it" else it }

private fun String?.toGenres(): String? = this?.split(",")?.joinToString { it.trim() }?.ifEmpty { null }
