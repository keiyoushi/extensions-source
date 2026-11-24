package eu.kanade.tachiyomi.extension.zh.relamanhua

import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val CHINA = ZoneId.of("Asia/Shanghai")

@Serializable
class ResponseDto(
    val code: Int,
    val message: String,
    val results: JsonElement,
)

@Serializable
class ComicListDto(
    private val list: List<ComicDto>,
    private val total: Int,
    private val offset: Int,
) {
    fun toMangasPage() = MangasPage(list.map { it.toSManga() }, offset + list.size < total)
}

@Serializable
class ComicDetailsDto(
    val comic: ComicDto,
    val groups: Map<String, GroupDto>,
)

@Serializable
class ComicDto(
    private val name: String,
    @SerialName("path_word") private val pathWord: String,
    private val cover: String? = null,
    private val alias: String? = null,
    private val author: List<TagDto> = emptyList(),
    private val theme: List<TagDto> = emptyList(),
    private val females: List<TagDto> = emptyList(),
    private val males: List<TagDto> = emptyList(),
    private val brief: String? = null,
    private val status: ValueDto? = null,
    private val region: ValueDto? = null,
    private val restrict: ValueDto? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = pathWord
        title = name
        thumbnail_url = cover
    }

    fun toSMangaDetails() = toSManga().apply {
        author = this@ComicDto.author.joinToString { it.name }.ifEmpty { null }
        genre = (theme + females + males).joinToString { it.name }.ifEmpty { null }
        status = when (this@ComicDto.status?.value) {
            0 -> SManga.ONGOING
            1 -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        description = MarkdownDescription.Builder()
            .info(region?.display, restrict?.display)
            .description(brief)
            .altTitles(alias.orEmpty().split(',').map(String::trim).filter { it.isNotEmpty() && it != name }, "別名")
            .build()
    }
}

@Serializable
class TagDto(
    val name: String,
)

@Serializable
class ValueDto(
    val value: Int,
    val display: String,
)

@Serializable
class GroupDto(
    @SerialName("path_word") val pathWord: String,
    val name: String,
)

@Serializable
class ChapterListDto(
    val list: List<ChapterDto>,
    val total: Int,
)

@Serializable
class ChapterDto(
    private val uuid: String,
    private val name: String,
    @SerialName("comic_path_word") private val comicPathWord: String,
    @SerialName("datetime_created") private val datetimeCreated: String? = null,
) {
    fun toSChapter(group: GroupDto) = SChapter.create().apply {
        url = "$comicPathWord/$uuid"
        name = if (group.pathWord == "default") this@ChapterDto.name else "${group.name}：${this@ChapterDto.name}"
        date_upload = DateTimeFormatter.ISO_LOCAL_DATE.tryParseDate(datetimeCreated, CHINA)
    }
}

@Serializable
class ChapterResultDto(
    @SerialName("is_lock") val isLock: Boolean,
    val chapter: ChapterContentsDto,
)

@Serializable
class ChapterContentsDto(
    val contents: List<ImageDto>,
)

@Serializable
class ImageDto(
    val url: String,
)
