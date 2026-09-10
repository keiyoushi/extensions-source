package eu.kanade.tachiyomi.extension.ja.kadocomi

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.collections.map
import kotlin.collections.orEmpty
import kotlin.time.Instant

@Serializable
class SeriesResponse(
    val result: List<Result>,
    private val total: Int?,
    private val pagination: Pagination?,
) {
    fun hasNextPage(offset: Int, limit: Int): Boolean = offset + limit < (total ?: pagination?.total ?: 0)
}

@Serializable
class Pagination(
    val total: Int,
)

@Serializable
class Result(
    private val code: String,
    private val originalThumbnail: String?,
    private val bookCover: String?,
    private val title: String,
) {
    fun toSManga() = SManga.create().apply {
        url = "/detail/$code"
        title = this@Result.title
        thumbnail_url = bookCover ?: originalThumbnail
    }
}

@Serializable
class DetailsResponse(
    val work: Work,
    val latestEpisodes: LatestEpisodes,
)

@Serializable
class Work(
    val code: String,
    private val originalThumbnail: String?,
    private val bookCover: String?,
    private val title: String,
    private val nextUpdateDateText: String?,
    private val serializationStatus: String?,
    private val tags: List<Genres>?,
    private val genre: Genres?,
    private val subGenre: Genres?,
    private val summary: String?,
    private val authors: List<Author>?,
) {
    fun toSManga() = SManga.create().apply {
        title = this@Work.title
        thumbnail_url = bookCover ?: originalThumbnail
        description = buildString {
            summary?.let { append(it) }
            nextUpdateDateText?.let { append("\n\nNext Update: $nextUpdateDateText") }
        }
        author = authors.orEmpty().filter { it.role in AUTHOR_ROLES }.joinToString { it.name }.ifEmpty { null }
        artist = authors.orEmpty().filter { it.role in ARTIST_ROLES }.joinToString { it.name }.ifEmpty { null }
        genre = listOfNotNull(this@Work.genre?.name, subGenre?.name)
            .plus(tags.orEmpty().map { it.name })
            .joinToString()
        status = when (serializationStatus?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "finished" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    companion object {
        // 著者 covers both roles at once
        private val AUTHOR_ROLES = setOf("原作", "著者")
        private val ARTIST_ROLES = setOf("漫画", "作画", "著者")
    }
}

@Serializable
class Genres(
    val name: String,
)

@Serializable
class Author(
    val name: String,
    val role: String,
)

@Serializable
class LatestEpisodes(
    val result: List<Episode>,
)

@Serializable
class Episode(
    private val id: String,
    private val code: String,
    private val title: String,
    private val subTitle: String?,
    private val updateDate: String?,
    private val internal: Internal,
    val isActive: Boolean,
) {
    fun toSChapter(workCode: String) = SChapter.create().apply {
        val lock = if (!isActive) "🔒 " else ""
        val chapterName = if (subTitle.isNullOrEmpty()) title else "$title - $subTitle"
        url = id
        name = lock + chapterName
        date_upload = Instant.tryParse(updateDate)
        chapter_number = internal.episodeNo.toFloat()
        memo = buildJsonObject {
            put("workCode", workCode)
            put("episodeCode", code)
        }
    }
}

@Serializable
class Internal(
    val episodeNo: Int,
)

@Serializable
class ViewerResponse(
    val manuscripts: List<Manuscript>,
)

@Serializable
class Manuscript(
    val drmHash: String,
    val drmImageUrl: String,
)
