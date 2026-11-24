package eu.kanade.tachiyomi.extension.all.plumics

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
class ProjectListDto(
    val data: List<ProjectDto>,
    @SerialName("next_page_url") val nextPageUrl: String? = null,
)

@Serializable
class ProjectDto(
    private val name: String,
    private val slug: String,
    private val cover: String? = null,
    val category: String,
    val lang: String,
    @SerialName("updated_at") val updatedAt: String? = null,
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        url = "/$category/$slug"
        title = name
        thumbnail_url = cover?.let { "$baseUrl/storage/$it" }
    }
}

@Serializable
class ProjectResponse(
    val project: ProjectDetailsDto,
)

@Serializable
class ProjectDetailsDto(
    val id: Int,
    private val name: String,
    private val slug: String,
    private val cover: String? = null,
    val category: String,
    val lang: String,
    @SerialName("author_name") private val authorName: String? = null,
    private val author: UserDto? = null,
    private val user: UserDto? = null,
    private val description: String? = null,
    private val genre: String? = null,
    private val status: String? = null,
    private val rating: Float? = null,
    @SerialName("reviews_count") private val reviewsCount: Int? = null,
    @SerialName("chapter_mode") val chapterMode: Boolean,
    @SerialName("created_at") private val createdAt: String? = null,
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        val creator = authorName ?: this@ProjectDetailsDto.author?.name
        url = "/$category/$slug"
        title = name
        thumbnail_url = cover?.let { "$baseUrl/storage/$it" }
        author = creator
        description = MarkdownDescription.Builder()
            .rating(rating?.takeIf { (reviewsCount ?: 0) > 0 }, max = 5, votes = reviewsCount)
            .description(this@ProjectDetailsDto.description)
            .field("Publisher", user?.name?.takeIf { it != creator })
            .build()
        genre = (listOf(CATEGORIES[category] ?: category) + this@ProjectDetailsDto.genre.orEmpty().split(","))
            .filter { it.isNotBlank() }
            .joinToString()
        status = when (this@ProjectDetailsDto.status) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "paused" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }

    // A project without chapter mode holds its pages itself, read as a single chapter.
    fun toSingleChapter() = SChapter.create().apply {
        url = "/reader/p/$id"
        name = "Chapter"
        date_upload = Instant.tryParse(createdAt)
    }
}

@Serializable
class UserDto(
    val name: String,
)

@Serializable
class ChapterListDto(
    val data: List<ChapterDto>,
    private val total: Int,
    @SerialName("per_page") private val perPage: Int,
) {
    val lastPage get() = (total + perPage - 1) / perPage
}

@Serializable
class ChapterDto(
    private val id: Int,
    private val number: Float,
    private val title: String? = null,
    private val coins: Int = 0,
    @SerialName("published_at") private val publishedAt: String? = null,
) {
    fun toSChapter() = SChapter.create().apply {
        url = "/reader/c/$id"
        name = buildString {
            // Paid chapters have no pages until they are unlocked with coins.
            if (coins > 0) append("🔒 ")
            append("Chapter ", number.toString().removeSuffix(".0"))
            if (!title.isNullOrBlank()) append(": ", title.trim())
        }
        chapter_number = number
        date_upload = Instant.tryParse(publishedAt)
    }
}

@Serializable
class ReaderDto(
    val chapter: ReaderChapterDto,
)

@Serializable
class ReaderChapterDto(
    @SerialName("project_id") val projectId: Int,
)

@Serializable
class PageListDto(
    val pages: List<PageDto>,
)

@Serializable
class PageDto(
    val id: Int,
)

private val CATEGORIES = mapOf(
    "manga" to "Manga",
    "comics" to "Comics",
    "webtoon" to "Webtoon",
    "bd" to "BD",
)
