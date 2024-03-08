package eu.kanade.tachiyomi.multisrc.gmanga

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

@Serializable
class EncryptedResponse(val data: String)

@Serializable
class MangaDataAction<T>(val mangaDataAction: T)

@Serializable
class LatestChaptersDto(
    val releases: List<LatestReleaseDto>,
)

@Serializable
class LatestReleaseDto(
    val manga: BrowseManga,
)

@Serializable
class SearchMangaDto(
    val mangas: List<BrowseManga>,
)

@Serializable
class BrowseManga(
    private val id: Int,
    private val title: String,
    private val cover: String,
) {

    fun toSManga(createThumbnail: (String, String) -> String) = SManga.create().apply {
        url = "/mangas/$id"
        title = this@BrowseManga.title
        thumbnail_url = createThumbnail(id.toString(), cover)
    }
}

@Serializable
class FiltersDto(
    val categoryTypes: List<FiltersDto>? = null,
    val categories: List<FilterDto>? = null,
)

@Serializable
class FilterDto(
    val name: String,
    val id: Int,
)

@Serializable
class MangaDetailsDto(
    val mangaData: Manga,
)

@Serializable
class Manga(
    private val id: Int,
    private val cover: String,
    private val title: String,
    private val summary: String? = null,
    private val artists: List<NameDto>,
    private val authors: List<NameDto>,
    @SerialName("story_status") private val status: Int,
    private val type: TypeDto,
    private val categories: List<NameDto>,
    @SerialName("translation_status") private val tlStatus: Int,
    private val synonyms: String? = null,
    @SerialName("arabic_title") private val arTitle: String? = null,
    @SerialName("japanese") private val jpTitle: String? = null,
    @SerialName("english") private val enTitle: String? = null,
) {
    fun toSManga(createThumbnail: (String, String) -> String) = SManga.create().apply {
        title = this@Manga.title
        thumbnail_url = createThumbnail(id.toString(), cover)
        artist = artists.joinToString { it.name }
        author = authors.joinToString { it.name }
        status = when (this@Manga.status) {
            2 -> SManga.ONGOING
            3 -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        genre = buildList {
            add(type.title)
            add(type.name)
            categories.forEach { add(it.name) }
        }.joinToString()
        description = buildString {
            summary.orEmpty()
                .ifEmpty { "لم يتم اضافة قصة بعد" }
                .also { append(it) }

            when (tlStatus) {
                0 -> "منتهية"
                1 -> "مستمرة"
                2 -> "متوقفة"
                else -> "مجهول"
            }.also {
                append("\n\n")
                append("حالة الترجمة")
                append(":\n• ")
                append(it)
            }

            val titles = listOfNotNull(synonyms, arTitle, jpTitle, enTitle)
            if (titles.isNotEmpty()) {
                append("\n\n")
                append("مسميّات أخرى")
                append(":\n• ")
                append(titles.joinToString("\n• "))
            }
        }
    }
}

@Serializable
class NameDto(val name: String)

@Serializable
class TypeDto(
    val name: String,
    val title: String,
)

@Serializable
class TableDto(
    private val cols: List<String>,
    private val rows: List<JsonElement>,
    private val isObject: Boolean? = null,
) {
    private fun TableDto.get(key: String, json: Json): TableDto? {
        isObject ?: return null

        val index = cols.indexOf(key)
        return json.decodeFromJsonElement(rows[index])
    }

    fun asChapterList(json: Json) = ChapterListDto(
        get("releases", json)!!.rows.map {
            ReleaseDto(
                it.jsonArray[0].jsonPrimitive.int,
                it.jsonArray[2].jsonPrimitive.long,
                it.jsonArray[3].jsonPrimitive.int,
                it.jsonArray[4].jsonPrimitive.int,
                it.jsonArray[5].jsonPrimitive.int,
            )
        },
        get("teams", json)!!.rows.map {
            TeamDto(
                it.jsonArray[0].jsonPrimitive.int,
                it.jsonArray[1].jsonPrimitive.content,
            )
        },
        get("chapterizations", json)!!.rows.map {
            ChapterDto(
                it.jsonArray[0].jsonPrimitive.int,
                it.jsonArray[1].jsonPrimitive.float,
                it.jsonArray[3].jsonPrimitive.content,
            )
        },
    )
}

class ReleaseDto(
    val id: Int,
    val timestamp: Long,
    val views: Int,
    val chapterizationId: Int,
    val teamId: Int,
)

class TeamDto(
    val id: Int,
    val name: String,
)

class ChapterDto(
    val id: Int,
    val chapter: Float,
    val title: String,
)

class ChapterListDto(
    val releases: List<ReleaseDto>,
    val teams: List<TeamDto>,
    val chapters: List<ChapterDto>,
)

@Serializable
class ReaderDto(
    val readerDataAction: ReaderData,
)

@Serializable
class ReaderData(
    val readerData: ReaderChapter,
)

@Serializable
class ReaderChapter(
    val release: ReaderPages,
)

@Serializable
class ReaderPages(
    @SerialName("webp_pages") val webpPages: List<String>,
    val pages: List<String>,
    @SerialName("storage_key") val key: String,
)
