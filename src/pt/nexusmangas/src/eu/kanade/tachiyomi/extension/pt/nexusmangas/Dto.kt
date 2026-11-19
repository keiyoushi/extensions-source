package eu.kanade.tachiyomi.extension.pt.nexusmangas

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
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
    private val description: String?,
    @SerialName("cover_url") private val coverUrl: String?,
    @SerialName("alternative_title") private val alternativeTitle: String?,
    private val status: String?,
    private val type: String?,
    private val author: String?,
    private val artist: String?,
    private val demographic: String?,
    private val scan: ScanDto?,
    @SerialName("work_genres") private val workGenres: List<WorkGenreDto> = emptyList(),
) {
    fun toSManga(mediaUrl: HttpUrl) = SManga.create().apply {
        url = slug
        title = this@WorkDto.title
        thumbnail_url = coverUrl?.takeIf(String::isNotBlank)?.let {
            mediaUrl.newBuilder().addQueryParameter("key", it).build().toString()
        }
        author = this@WorkDto.author?.takeIf(String::isNotBlank)
        artist = this@WorkDto.artist?.takeIf(String::isNotBlank)
        description = MarkdownDescription.Builder()
            .description(this@WorkDto.description)
            .altTitles(listOfNotNull(alternativeTitle), "Título alternativo")
            .field("Scan", scan?.name)
            .build()
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
}

@Serializable
class ScanDto(
    val name: String?,
)

@Serializable
class WorkGenreDto(
    val genres: GenreDto?,
)

@Serializable
class GenreDto(
    val name: String,
)

@Serializable
class ChapterDto(
    private val id: String,
    private val number: Float?,
    @SerialName("chapter_label") private val label: String?,
    private val title: String?,
    @SerialName("published_at") private val publishedAt: String?,
) {
    fun toSChapter(workSlug: String) = SChapter.create().apply {
        val numberLabel = number?.toString()?.removeSuffix(".0")
        url = if (numberLabel != null) "/capitulo/$workSlug/$numberLabel" else "${NexusMangas.READ_PATH}$id"
        name = buildString {
            append(numberLabel?.let { "Capítulo $it" } ?: label!!)
            this@ChapterDto.title?.takeIf(String::isNotBlank)?.let { append(" - $it") }
        }
        number?.let { chapter_number = it }
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
    private val chapter: ReadChapterPagesDto?,
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
