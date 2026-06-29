package eu.kanade.tachiyomi.extension.ar.kawiimanga

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Serializable
class MangaList(
    val results: List<Manga>,
    val hasMore: Boolean = false,
)

@Serializable
class Manga(
    val slug: String,
    private val title: String,
    private val description: String? = null,
    private val coverUrl: String? = null,
    private val author: String? = null,
    private val artist: String? = null,
    private val type: String? = null,
    private val status: String? = null,
    private val genres: List<String> = emptyList(),
    val chapters: List<Chapter> = emptyList(),
) {

    fun toSManga() = SManga.create().apply {
        url = slug
        title = this@Manga.title
        thumbnail_url = coverUrl
        author = this@Manga.author?.takeUnless { it.isEmpty() }
        artist = this@Manga.artist?.takeUnless { it.isEmpty() }
        description = this@Manga.description?.takeUnless { it.isEmpty() }
        genre = getGenres()
        status = getStatus()
        initialized = true
    }

    private fun getStatus() = when (status) {
        "ongoing", "coming_soon" -> SManga.ONGOING
        "completed" -> SManga.COMPLETED
        "cancelled", "dropped" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    private fun getGenres() = buildList {
        when (type) {
            "manga" -> add("Manga")
            "manhua" -> add("Manhua")
            "manhwa" -> add("Manhwa")
            else -> {}
        }
        addAll(genres)
    }.distinct().joinToString()
}

@Serializable
class Chapter(
    private val id: String,
    private val title: String,
    private val number: Int,
    private val createdAt: String,
) {

    fun toSChapter(slug: String) = SChapter.create().apply {
        url = "$slug/$number#$id"
        name = buildString {
            append("الفصل $number")
            if (this.toString() != title) append(" - $title")
        }
        date_upload = dateFormat.tryParse(createdAt)
    }
}

@Serializable
class Pages(
    val pages: List<String>,
)

private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}
