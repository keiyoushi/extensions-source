package eu.kanade.tachiyomi.multisrc.iken

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import org.jsoup.Jsoup
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

@Serializable
class SearchResponse(
    val posts: List<Manga>,
    val totalCount: Int,
)

@Serializable
class Manga(
    private val id: Int,
    val slug: String,
    private val postTitle: String,
    private val postContent: String? = null,
    val isNovel: Boolean,
    private val featuredImage: String? = null,
    private val alternativeTitles: String? = null,
    private val author: String? = null,
    private val artist: String? = null,
    private val seriesType: String? = null,
    private val seriesStatus: String? = null,
    val genres: List<Genre> = emptyList(),
) {
    fun toSManga() = SManga.create().apply {
        url = "$slug#$id"
        title = postTitle
        thumbnail_url = featuredImage
        author = this@Manga.author?.takeUnless { it.isEmpty() }
        artist = this@Manga.artist?.takeUnless { it.isEmpty() }
        description = buildString {
            postContent?.takeUnless { it.isEmpty() }?.let { desc ->
                val tmpDesc = desc.replace("\n", "<br>")

                append(Jsoup.parse(tmpDesc).text())
            }
            alternativeTitles?.takeUnless { it.isEmpty() }?.let { altName ->
                append("\n\n")
                append("Alternative Names: ")
                append(altName)
            }
        }.trim()
        genre = getGenres()
        status = when (seriesStatus) {
            "ONGOING", "COMING_SOON" -> SManga.ONGOING
            "COMPLETED" -> SManga.COMPLETED
            "CANCELLED", "DROPPED" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        initialized = true
    }

    fun getGenres() = buildList {
        when (seriesType) {
            "MANGA" -> add("Manga")
            "MANHUA" -> add("Manhua")
            "MANHWA" -> add("Manhwa")
            else -> {}
        }
        genres.forEach { add(it.name) }
    }.distinct().joinToString()
}

@Serializable
class Genre(
    val id: Int,
    val name: String,
)

@Serializable
class Name(val name: String)

@Serializable
class Post<T>(val post: T)

@Serializable
class ChapterListResponse(
    val isNovel: Boolean,
    val slug: String,
    val chapters: List<Chapter>,
)

@Serializable
class Chapter(
    private val id: Int,
    private val slug: String,
    private val number: JsonPrimitive,
    private val createdBy: Name,
    private val createdAt: String,
    private val chapterStatus: String,
) {
    fun isPublic() = chapterStatus == "PUBLIC"

    fun toSChapter(mangaSlug: String) = SChapter.create().apply {
        url = "/series/$mangaSlug/$slug#$id"
        name = "Chapter $number"
        scanlator = createdBy.name
        date_upload = try {
            dateFormat.parse(createdAt)!!.time
        } catch (_: ParseException) {
            0L
        }
    }
}

private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH)
