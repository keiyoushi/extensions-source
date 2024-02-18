package eu.kanade.tachiyomi.extension.en.mangaowlto

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
data class MangaOwlTitle(
    val id: String,
    val name: String,
    val slug: String,
    @SerialName("status") val titleStatus: String, // ongoing & completed
    @SerialName("thumbnail") val thumbnailUrl: String,
    @SerialName("al_name") val altName: String?,
    val rating: Float,
    val view_count: Int,
    val description: String,
    val type: String, // manga & comic
    val genres: List<MangaOwlGenre> = emptyList(),
    val authors: List<MangaOwlAuthor> = emptyList(),
    val chapters: List<MangaOwlChapter> = emptyList(),
    val latest_chapter: MangaOwlChapter,
    val created_at: String,
    val modified_at: String,
    val data_status: Int,
) {
    private val fullDescription: String
        get() = buildString {
            append(description)
            altName?.let { append("\n\n $it") }
            append("\n\nRating: $rating")
            append("\nViews: $view_count")
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
        url = "/manga/$id"
    }
}

@Serializable
data class MangaOwlGenre(
    val id: Int,
    val name: String,
)

@Serializable
data class MangaOwlAuthor(
    val id: Int,
    val name: String,
    val created_at: String,
    val modified_at: String,
)

@Serializable
data class MangaOwlChapter(
    val id: Int,
    @SerialName("name") val title: String,
    val created_at: String,
    val order: Int,
    val views: Int,
) {
    fun toSChapter(slug: String): SChapter = SChapter.create().apply {
        name = title
        date_upload = dateFormat.tryParse(created_at)
        url = "/reading/$slug/$id"
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
data class MangaOwlChapterPages(
    @SerialName("results") val pages: List<MangaOwlPage> = emptyList(),
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
data class MangaOwlPage(
    @SerialName("image") val imageUrl: String,
)
