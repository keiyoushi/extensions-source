package eu.kanade.tachiyomi.extension.id.doujindesuunoriginal

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale

@Serializable
class MangaList(
    val mangas: List<Manga>,
    val totalItems: Int? = null,
) {
    @Serializable
    class Manga(
        val slug: String,
        val title: String,
        val thumb: String? = null,
    ) {
        fun toSManga() = SManga.create().apply {
            url = slug
            title = this@Manga.title
            thumbnail_url = thumb
        }
    }

    fun hasNextPage(page: Int): Boolean = totalItems != null && page * 24 < totalItems
}

@Serializable
class MangaDetails(
    val manga: Manga,
) {
    @Serializable
    class Manga(
        val slug: String,
        val title: String,
        val thumb: String? = null,
        val author: String? = null,
        val status: String? = null,
        val tags: List<String> = emptyList(),
        val rating: Double? = null,
        val synopsis: String? = null,
        val alternativeTitle: String? = null,
    ) {
        fun toSManga() = SManga.create().apply {
            url = slug
            title = this@Manga.title
            thumbnail_url = thumb
            author = this@Manga.author?.takeIf { it.isNotBlank() && it != "Unknown" }
            description = buildString {
                rating?.let { append("Rating: ", it, "/10\n\n") }
                synopsis?.takeIf { it.isNotBlank() }?.let { append(it, "\n\n") }
                alternativeTitle?.takeIf { it.isNotBlank() }?.let { append("Judul Alternatif: ", it) }
            }
            genre = tags.joinToString()
            status = when (this@Manga.status?.lowercase()) {
                "publishing", "ongoing" -> SManga.ONGOING
                "finished", "completed" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }
    }
}

@Serializable
class ChaptersList(
    val chapters: List<Chapter>,
) {
    @Serializable
    class Chapter(
        val slug: String,
        val title: String,
        private val createdAt: String? = null,
    ) {
        fun toSChapter(mangaSlug: String) = SChapter.create().apply {
            url = "/read/$mangaSlug/$slug"
            name = if (title.startsWith("Chapter", ignoreCase = true) || (title.any { it.isLetter() } && !title.first().isDigit())) {
                title
            } else {
                "Chapter $title"
            }
            date_upload = dateFormat.tryParse(createdAt?.replace("Z", "+00:00"))
        }
    }
}

private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZZZZZ", Locale.ROOT)

@Serializable
class ReaderData(
    val data: Data? = null,
) {
    @Serializable
    class Data(
        val chapter: Chapter,
    ) {
        @Serializable
        class Chapter(
            val images: List<String> = emptyList(),
        )
    }
}
