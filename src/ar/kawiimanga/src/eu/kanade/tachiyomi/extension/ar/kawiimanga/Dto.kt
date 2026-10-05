package eu.kanade.tachiyomi.extension.ar.kawiimanga

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Instant

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
        author = this@Manga.author.takeIfValid()
        artist = this@Manga.artist.takeIfValid()
        description = this@Manga.description?.takeUnless { it.isEmpty() }
        genre = getGenres()
        status = getStatus()
        initialized = true
    }

    private fun String?.takeIfValid(): String? = this?.takeUnless { it.isBlank() || it.equals("unknown", true) }

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
        url = "$slug/$number"
        val baseName = "الفصل $number"
        name = if (title == baseName) baseName else "$baseName - $title"
        chapter_number = number.toFloat()
        date_upload = Instant.tryParse(createdAt)
        memo = buildJsonObject { put("id", id) }
    }
}

@Serializable
class Pages(
    val pages: List<String>,
)

@Serializable
class Token(
    val token: String,
    val expiresIn: Int,
)
