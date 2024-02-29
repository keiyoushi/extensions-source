package eu.kanade.tachiyomi.extension.en.mangaowlto

import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

@Serializable
class MangaOwlToStories(
    private val next: String?,
    private val results: List<MangaOwlToStory>,
) {
    fun toMangasPage() = MangasPage(
        mangas = results.map { it.toSManga() },
        hasNextPage = !next.isNullOrEmpty(),
    )
}

@Serializable
class MangaOwlToStory(
    private val name: String,
    private val slug: String,
    @SerialName("status") private val titleStatus: String?, // ongoing & completed
    @SerialName("thumbnail") private val thumbnailUrl: String,
    @SerialName("al_name") private val altName: String?,
    private val rating: Float?,
    @SerialName("view_count") private val views: Int,
    private val description: String?,
    private val genres: List<MangaOwlToGenre> = emptyList(),
    private val authors: List<MangaOwlToAuthor> = emptyList(),
    private val chapters: List<MangaOwlToChapter> = emptyList(),
) {
    private val fullDescription: String
        get() = buildString {
            append(description)
            altName?.let { append("\n\n $it") }
            append("\n\nRating: $rating")
            append("\nViews: $views")
        }

    val chaptersList: List<SChapter>
        get() = chapters.reversed().map { it.toSChapter(slug) }

    fun toSManga(): SManga = SManga.create().apply {
        title = name
        author = authors.joinToString { it.name }
        description = fullDescription.trim()
        genre = genres.joinToString { it.name }
        status = when (titleStatus) {
            MangaOwlTo.ONGOING -> SManga.ONGOING
            MangaOwlTo.COMPLETED -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        thumbnail_url = thumbnailUrl
        url = slug
    }
}

@Serializable
class MangaOwlToGenre(
    val name: String,
)

@Serializable
class MangaOwlToAuthor(
    val name: String,
)

@Serializable
class MangaOwlToChapter(
    private val id: Int,
    @SerialName("name") private val title: String,
    @SerialName("created_at") private val createdAt: String,
) {
    fun toSChapter(slug: String): SChapter = SChapter.create().apply {
        name = title
        date_upload = parseDate()
        url = "/reading/$slug/$id"
    }

    companion object {
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.US)
    }

    private fun parseDate(): Long = try {
        dateFormat.parse(createdAt)!!.time
    } catch (_: ParseException) {
        0L
    }
}

@Serializable
class MangaOwlToChapterPages(
    @SerialName("results") private val pages: List<MangaOwlToPage> = emptyList(),
) {
    fun toPages() =
        pages.mapIndexed { idx, page ->
            Page(
                index = idx,
                imageUrl = page.imageUrl,
            )
        }
}

@Serializable
class MangaOwlToPage(
    @SerialName("image") val imageUrl: String,
)
