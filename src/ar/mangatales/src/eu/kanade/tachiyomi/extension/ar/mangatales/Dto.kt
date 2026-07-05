package eu.kanade.tachiyomi.extension.ar.mangatales

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.float
import java.text.SimpleDateFormat
import java.util.Locale

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
    private val cover: String? = null,
    @SerialName("is_novel") val isNovel: Boolean,
) {
    fun toSManga(createThumbnail: (String, String) -> String) = SManga.create().apply {
        url = "/mangas/$id"
        title = this@BrowseManga.title
        thumbnail_url = cover?.let { createThumbnail(id.toString(), cover) }
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
    private val cover: String? = null,
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
        thumbnail_url = cover?.let { createThumbnail(id.toString(), cover) }
        artist = artists.joinToString { it.name }
        author = authors.joinToString { it.name }
        status = when (this@Manga.status) {
            2 -> SManga.ONGOING
            3 -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        genre = buildList {
            type.title?.let { add(it) }
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
                .filterNot(String::isEmpty)

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
    val title: String?,
)

@Serializable
class ChapterListDto(
    val mangaReleases: List<ChapterRelease>,
)

@Serializable
class ChapterRelease(
    private val id: Int,
    private val chapter: JsonPrimitive,
    private val title: String,
    @SerialName("team_name") private val teamName: String,
    @SerialName("created_at") private val createdAt: String,
) {
    fun toSChapter() = SChapter.create().apply {
        url = "/r/$id"
        chapter_number = chapter.float
        date_upload = try {
            dateFormat.parse(createdAt)!!.time
        } catch (_: Exception) {
            0L
        }
        scanlator = teamName

        val chapterName = title.let { if (it.trim() != "") " - $it" else "" }
        name = "${chapter_number.let { if (it % 1 > 0) it else it.toInt() }}$chapterName"
    }
}

private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH)

@Serializable
class ReaderDto(
    val readerDataAction: ReaderData,
    val globals: Globals,
)

@Serializable
class Globals(
    val mediaKey: String,
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
    @SerialName("hq_pages") private val page: String,
) {
    val pages get() = page.split("\r\n")
}
