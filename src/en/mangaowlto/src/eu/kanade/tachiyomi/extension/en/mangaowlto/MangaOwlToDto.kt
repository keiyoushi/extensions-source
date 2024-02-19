package eu.kanade.tachiyomi.extension.en.mangaowlto

import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.DateFormat
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

@Serializable
data class MangaOwlToStories(
    val count: Int,
    val next: String?,
    val previous: String?,
    val results: List<MangaOwlToStory>,
) {
    fun toMangasPage() = MangasPage(
        mangas = results.map { it.toSManga() },
        hasNextPage = !next.isNullOrEmpty(),
    )
}

@Serializable
data class MangaOwlToStory(
    val id: String,
    val name: String,
    val slug: String,
    @SerialName("status") val titleStatus: String?, // ongoing & completed
    @SerialName("thumbnail") val thumbnailUrl: String,
    @SerialName("al_name") val altName: String?,
    val rating: Float?,
    val view_count: Int,
    val description: String?,
    val type: String?, // manga & comic
    val genres: List<MangaOwlToGenre> = emptyList(),
    val authors: List<MangaOwlToAuthor> = emptyList(),
    val chapters: List<MangaOwlToChapter> = emptyList(),
    val latest_chapter: MangaOwlToChapter?,
    val created_at: String,
    val modified_at: String,
) {
    private val fullDescription: String
        get() = buildString {
            append(description)
            altName?.let { append("\n\n $it") }
            append("\n\nRating: $rating")
            append("\nViews: $view_count")
        }

    val chaptersList: List<SChapter>
        get() = chapters.reversed().map { it.toSChapter() }

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
        url = "/stories/$slug"
    }
}

@Serializable
data class MangaOwlToGenre(
    val id: Int,
    val name: String,
)

@Serializable
data class MangaOwlToAuthor(
    val id: Int,
    val name: String,
    val created_at: String,
    val modified_at: String,
)

@Serializable
data class MangaOwlToChapter(
    val id: Int,
    @SerialName("name") val title: String,
    val created_at: String,
    val order: Int,
    val views: Int,
) {
    fun toSChapter(): SChapter = SChapter.create().apply {
        name = title
        date_upload = dateFormat.tryParse(created_at)
        url = "/chapters/$id/images?page_size=1000"
    }

    companion object {
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.US)
    }

    private fun DateFormat.tryParse(str: String): Long = try {
        parse(str)!!.time
    } catch (_: ParseException) {
        0L
    }
}

@Serializable
data class MangaOwlToChapterPages(
    @SerialName("results") val pages: List<MangaOwlToPage> = emptyList(),
    val count: Int,
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
data class MangaOwlToPage(
    @SerialName("image") val imageUrl: String,
)
