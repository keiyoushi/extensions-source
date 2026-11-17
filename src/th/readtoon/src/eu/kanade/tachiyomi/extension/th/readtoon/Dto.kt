package eu.kanade.tachiyomi.extension.th.readtoon

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import kotlin.time.Instant

@Serializable
class TrpcInput<T>(
    private val json: T,
    private val meta: TrpcMeta? = null,
)

@Serializable
class TrpcMeta(private val values: Map<String, List<String>>)

@Serializable
class TrpcResponse<T>(private val result: TrpcResult<T>) {
    val data get() = result.data.json
}

@Serializable
class TrpcResult<T>(val data: TrpcData<T>)

@Serializable
class TrpcData<T>(val json: T)

@Serializable
class ListQuery(
    private val q: String,
    private val sort: String,
    private val order: String,
    private val page: Int,
    private val limit: Int,
    private val contentType: String,
    private val genres: List<String>? = null,
)

@Serializable
class StatsQuery(
    private val sort: String,
    private val startDate: String,
    private val endDate: String,
    private val order: String,
    private val page: Int,
    private val limit: Int,
    private val contentType: String,
)

@Serializable
class ContentListDto(
    val content: List<ContentDto>,
    private val meta: ListMetaDto,
) {
    val hasNextPage get() = !meta.isLastPage
}

@Serializable
class ListMetaDto(val isLastPage: Boolean)

@Serializable
class ContentDto(
    private val title: String,
    private val slug: String,
    @SerialName("thumbnail_url") private val thumbnailUrl: String? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = slug
        title = this@ContentDto.title
        thumbnail_url = thumbnailUrl?.let { cdnUrl(it) }
    }
}

@Serializable
class ContentPageDto(
    private val title: String,
    private val slug: String,
    private val type: String,
    private val description: String? = null,
    private val thumbnailUrl: String? = null,
    private val genres: List<GenreDto> = emptyList(),
    private val attributes: List<AttributeDto> = emptyList(),
    val chapters: List<ChapterDto>,
) {
    val isManga get() = type == "manga"

    fun toSManga() = SManga.create().apply {
        url = slug
        title = this@ContentPageDto.title
        thumbnail_url = thumbnailUrl?.let { cdnUrl(it) }
        genre = genres.joinToString { it.name }.ifEmpty { null }
        status = when (attribute("status")) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        description = MarkdownDescription.Builder()
            .description(this@ContentPageDto.description?.let { Jsoup.parseBodyFragment(it).body() })
            .altTitles(attribute("alternative_title")?.split(", ")?.filter { it != "-" }.orEmpty())
            .build()
    }

    private fun attribute(key: String) = attributes.firstOrNull { it.key == key }?.value
}

@Serializable
class GenreDto(val name: String)

@Serializable
class AttributeDto(val key: String, val value: String)

@Serializable
class ChapterDto(
    private val no: Double,
    private val name: String? = null,
    private val isPaid: Boolean,
    private val publishedAt: String? = null,
    private val user: UserDto? = null,
) {
    fun toSChapter(slug: String) = SChapter.create().apply {
        val number = no.toString().removeSuffix(".0")
        url = "$slug/$number"
        val title = this@ChapterDto.name?.takeIf { it.isNotBlank() } ?: "ตอนที่ $number"
        name = if (isPaid) "🔒 $title" else title
        chapter_number = no.toFloat()
        date_upload = Instant.tryParse(publishedAt)
        scanlator = user?.nickname
    }
}

@Serializable
class UserDto(val nickname: String? = null)

@Serializable
class KeyQuery(
    private val contentSlug: String,
    private val chapterNo: Double,
    private val timestamp: Long,
)

@Serializable
class KeyInfoDto(
    val bot: Boolean,
    val html: String,
    val body: String,
)

@Serializable
class DecryptQuery(
    private val contentSlug: String,
    private val chapterNo: Double,
    private val air: String,
)

@Serializable
class DecryptDto(val data: String)

@Serializable
class PagesDto(
    private val base: JsonElement,
    val result: List<String>,
) {
    // The reader picks one of the image hosts at random
    fun host(): String = when (base) {
        is JsonArray -> base.random()
        is JsonObject -> base.values.random()
        else -> base
    }.jsonPrimitive.content
}

fun cdnUrl(path: String) = "$CDN_URL/${path.removePrefix("/")}"

private const val CDN_URL = "https://w.nobuild.pro"
