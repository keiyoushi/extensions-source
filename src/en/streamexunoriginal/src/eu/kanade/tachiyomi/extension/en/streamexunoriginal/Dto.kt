package eu.kanade.tachiyomi.extension.en.streamexunoriginal

import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup

@Serializable
class ListDto(
    private val results: List<MediaDto>,
    private val page: Int,
    @SerialName("total_pages") private val totalPages: Int,
) {
    fun toMangasPage() = MangasPage(results.map { it.toSManga() }, page < totalPages)
}

@Serializable
class MediaDto(
    private val id: Int,
    private val title: TitleDto,
    private val coverImage: CoverDto? = null,
    private val description: String? = null,
    private val status: String? = null,
    private val genres: List<String> = emptyList(),
    private val format: String? = null,
    private val staff: StaffDto? = null,
) {
    // Roles of the English editions ("Touch-up Art & Lettering (English)") are left out
    private fun staff(vararg roles: String) = staff?.edges.orEmpty()
        .filter { edge -> roles.any { edge.role.startsWith(it) } }
        .map { it.node.name.full }
        .distinct()

    fun toSManga() = SManga.create().apply {
        url = id.toString()
        title = this@MediaDto.title.main
        thumbnail_url = coverImage?.extraLarge ?: coverImage?.large
        author = staff("Story", "Original").joinToString().ifEmpty { null }
        artist = staff("Art", "Story & Art").joinToString().ifEmpty { null }
        genre = genres.joinToString().ifEmpty { null }
        description = MarkdownDescription.Builder()
            .description(this@MediaDto.description?.let { Jsoup.parseBodyFragment(it).body() })
            .field("Format", format?.replace('_', ' ')?.lowercase()?.replaceFirstChar(Char::uppercase))
            .altTitles(this@MediaDto.title.others)
            .build()
        status = when (this@MediaDto.status) {
            "RELEASING" -> SManga.ONGOING
            "FINISHED" -> SManga.COMPLETED
            "HIATUS" -> SManga.ON_HIATUS
            "CANCELLED" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }
}

@Serializable
class TitleDto(
    private val english: String? = null,
    private val romaji: String? = null,
    private val native: String? = null,
) {
    private val all get() = listOfNotNull(english, romaji, native).filter { it.isNotBlank() }.distinct()

    val main get() = all.first()

    val others get() = all.drop(1)
}

@Serializable
class CoverDto(
    val large: String? = null,
    val extraLarge: String? = null,
)

@Serializable
class StaffDto(
    val edges: List<StaffEdgeDto> = emptyList(),
)

@Serializable
class StaffEdgeDto(
    val role: String,
    val node: StaffNodeDto,
)

@Serializable
class StaffNodeDto(
    val name: NameDto,
)

@Serializable
class NameDto(
    val full: String,
)

@Serializable
class ChaptersDto(
    val chapters: List<ChapterDto> = emptyList(),
)

@Serializable
class ChapterDto(
    private val id: String,
    private val title: String,
    private val chapter: String? = null,
) {
    fun toSChapter(mangaId: String) = SChapter.create().apply {
        url = "/manga/read/$mangaId?chapterId=$id"
        name = title
        chapter_number = chapter?.toFloatOrNull() ?: -1f
    }
}

@Serializable
class PageDto(
    val img: String,
)
