package eu.kanade.tachiyomi.extension.pt.nexusmangas

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import java.io.IOException
import kotlin.time.Instant

@Serializable
class WorkDto(
    private val slug: String,
    private val title: String,
    private val description: String? = null,
    @SerialName("cover_url") private val coverUrl: String? = null,
    @SerialName("alternative_title") private val alternativeTitle: String? = null,
    private val status: String? = null,
    private val type: String? = null,
    private val author: String? = null,
    private val artist: String? = null,
    private val demographic: String? = null,
    private val scan: ScanDto? = null,
    @SerialName("work_genres") private val workGenres: List<WorkGenreDto> = emptyList(),
    private val chapters: List<ChapterDto> = emptyList(),
) {
    fun toSManga(mediaUrl: HttpUrl) = SManga.create().apply {
        url = slug
        title = this@WorkDto.title
        thumbnail_url = coverUrl?.takeIf(String::isNotBlank)?.let {
            mediaUrl.newBuilder().addQueryParameter("key", it).build().toString()
        }
        author = this@WorkDto.author?.takeIf(String::isNotBlank)
        artist = this@WorkDto.artist?.takeIf(String::isNotBlank)
        description = buildString {
            this@WorkDto.description?.takeIf(String::isNotBlank)?.let(::append)
            alternativeTitle?.takeIf(String::isNotBlank)?.let {
                if (isNotEmpty()) append("\n\n")
                append("Título alternativo: $it")
            }
            scan?.name?.takeIf(String::isNotBlank)?.let {
                if (isNotEmpty()) append("\n\n")
                append("Scan: $it")
            }
        }.takeIf(String::isNotBlank)
        genre = buildList {
            type?.let(::add)
            demographic?.takeIf { it != "NONE" }?.let(::add)
            workGenres.mapNotNullTo(this) { it.genres?.name }
        }.map(::humanize).distinct().joinToString()
        status = when (this@WorkDto.status) {
            "RELEASING" -> SManga.ONGOING
            "COMPLETED" -> SManga.COMPLETED
            "HIATUS" -> SManga.ON_HIATUS
            "CANCELLED" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }

    val chapterList get() = chapters.map { it.toSChapter(slug) }.sortedByDescending(SChapter::chapter_number)
}

@Serializable
class ScanDto(
    val name: String? = null,
)

@Serializable
class WorkGenreDto(
    val genres: GenreDto? = null,
)

@Serializable
class GenreDto(
    val name: String,
)

@Serializable
class ChapterDto(
    private val number: Float,
    private val title: String? = null,
    @SerialName("published_at") private val publishedAt: String? = null,
) {
    fun toSChapter(workSlug: String) = SChapter.create().apply {
        val label = number.toString().removeSuffix(".0")
        url = "/capitulo/$workSlug/$label"
        name = buildString {
            append("Capítulo $label")
            this@ChapterDto.title?.takeIf(String::isNotBlank)?.let { append(" - $it") }
        }
        chapter_number = number
        date_upload = Instant.tryParse(publishedAt)
    }
}

@Serializable
class ChapterIdDto(
    val id: String,
)

@Serializable
class ReadChapterRequestDto(
    private val chapterId: String,
)

@Serializable
class ReadChapterDto(
    private val access: AccessDto,
    private val chapter: ReadChapterPagesDto? = null,
) {
    private val pages get() = chapter?.pages.orEmpty()

    fun toPageList(chapterId: String): List<Page> {
        if (!access.allowed) return emptyList()
        return pages.mapIndexed { index, url -> Page(index, imageUrl = "$url#$chapterId/$index") }
    }

    fun pageUrl(index: Int): String = pages.getOrNull(index) ?: throw IOException("Página não encontrada")
}

@Serializable
class AccessDto(
    val allowed: Boolean,
)

@Serializable
class ReadChapterPagesDto(
    val pages: List<String> = emptyList(),
)

private fun humanize(value: String): String = value.split('_')
    .joinToString(" ") { word -> word.lowercase().replaceFirstChar(Char::uppercase) }
