package eu.kanade.tachiyomi.extension.id.doujindesuunoriginal

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.text.SimpleDateFormat
import java.util.Locale

@Serializable
class MangaList(
    val mangas: List<Manga>,
    @SerialName("current_page")
    val currentPage: Int = 1,
    @SerialName("last_page")
    val lastPage: Int = 1,
) {
    @Serializable
    class Manga(
        val slug: String,
        val title: String,
        val thumb: String? = null,
        val genres: JsonElement? = null,
    ) {
        fun toSManga() = SManga.create().apply {
            url = slug
            title = this@Manga.title
            thumbnail_url = thumb
        }
    }

    fun hasNextPage() = currentPage < lastPage
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
        val genres: JsonElement? = null,
        val synopsis: String? = null,
        val alternativeTitle: String? = null,
    ) {
        fun toSManga() = SManga.create().apply {
            url = slug
            title = this@Manga.title
            thumbnail_url = thumb
            author = this@Manga.author?.takeIf { it.isNotBlank() && it != "Unknown" }
            description = buildString {
                synopsis?.takeIf { it.isNotBlank() }?.let { append(it, "\n\n") }
                alternativeTitle?.takeIf { it.isNotBlank() }?.let { append("Judul Alternatif: ", it) }
            }
            genre = try {
                genres?.jsonArray?.joinToString { it.jsonPrimitive.content }
            } catch (_: Exception) {
                null
            }
            status = when (this@Manga.status?.lowercase()) {
                "publishing", "ongoing" -> SManga.ONGOING
                "finished", "completed" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }
    }
}

@Serializable
class FilterData(
    val name: String,
)

@Serializable
class GenreList(
    val genres: List<FilterData>,
)

@Serializable
class ChaptersList(
    val chapters: List<Chapter>,
) {
    @Serializable
    class Chapter(
        val slug: String,
        val title: String,
        @SerialName("createdAt")
        private val createdAt: String? = null,
    ) {
        fun toSChapter(mangaSlug: String) = SChapter.create().apply {
            url = "/read/$mangaSlug/$slug"
            name = title
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
