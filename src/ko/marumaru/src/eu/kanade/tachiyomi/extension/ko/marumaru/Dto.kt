package eu.kanade.tachiyomi.extension.ko.marumaru

import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
class WorksPageDto(
    private val items: List<WorkDto>,
    private val total: Int,
    private val page: Int,
    private val size: Int,
) {
    fun toMangasPage() = MangasPage(items.map { it.toSManga() }, (page + 1) * size < total)
}

@Serializable
class WorkDto(
    private val id: Int,
    private val title: String,
    private val mediaType: String,
    private val status: String?,
    private val platform: String?,
    private val author: String?,
    private val genres: String?,
    private val coverUrl: String?,
) {
    val isComic get() = mediaType in COMIC_TYPES

    fun toSManga() = SManga.create().apply {
        url = "/${mediaType.lowercase()}/$id"
        title = this@WorkDto.title
        thumbnail_url = coverUrl
    }

    fun toSManga(synopsis: String?, illustrator: String?) = toSManga().apply {
        // Names come as "a / b" or "a," and the illustrator also has a field of its own
        author = this@WorkDto.author?.joinItems(NAME_SEPARATOR)
        artist = illustrator?.joinItems(NAME_SEPARATOR)
        description = MarkdownDescription.Builder()
            .info(platform)
            .description(synopsis)
            .build()
        genre = genres?.joinItems(GENRE_SEPARATOR)
        status = when (this@WorkDto.status) {
            "SERIALIZING" -> SManga.ONGOING
            "COMPLETED" -> SManga.COMPLETED
            "HIATUS" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }

    private fun String.joinItems(separator: Regex) = split(separator)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .joinToString()
        .ifEmpty { null }

    companion object {
        // The site also has novels and animations
        private val COMIC_TYPES = setOf(WEBTOON, MANHWA)

        private val NAME_SEPARATOR = Regex("""[,/]""")
        private val GENRE_SEPARATOR = Regex(""",""")
    }
}

@Serializable
class WorkDetailsDto(
    private val work: WorkDto,
    private val synopsis: String?,
    private val illustrator: String?,
) {
    val isComic get() = work.isComic

    fun toSManga() = work.toSManga(synopsis, illustrator)
}

@Serializable
class ReaderWorkDto(
    val episodes: List<EpisodeDto>,
)

@Serializable
class EpisodeDto(
    private val id: Long,
    private val number: Float,
    private val title: String?,
    @SerialName("published_at") private val publishedAt: String?,
    val ready: Boolean,
) {
    fun toSChapter(mangaUrl: String) = SChapter.create().apply {
        url = "$mangaUrl/$id"
        // The site names an untitled episode after its number
        name = title?.replace(SPACES, " ")?.trim()?.ifEmpty { null } ?: "${number.toString().removeSuffix(".0")}화"
        chapter_number = number
        date_upload = Instant.tryParse(publishedAt)
    }

    companion object {
        private val SPACES = Regex("""\s+""")
    }
}

@Serializable
class ReaderEpisodeDto(
    private val item: EpisodeBodyDto,
) {
    // An episode whose images are still being processed only holds a notice
    val images get() = item.body.filter { it.kind == "image" }.mapNotNull { it.url }
}

@Serializable
class EpisodeBodyDto(
    val body: List<BodyItemDto>,
)

@Serializable
class BodyItemDto(
    val kind: String,
    val url: String?,
)
