package eu.kanade.tachiyomi.extension.ar.kawiimanga

import android.util.Base64
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.aesDecrypt
import keiyoushi.utils.aesGcm
import keiyoushi.utils.parseAs
import keiyoushi.utils.sha256
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
    private val titleAr: String? = null,
    private val altTitles: List<String> = emptyList(),
    private val rating: Double? = null,
    val chapters: List<Chapter> = emptyList(),
) {

    fun toSManga() = SManga.create().apply {
        url = slug
        title = this@Manga.title
        thumbnail_url = coverUrl
        author = this@Manga.author.takeIfValid()
        artist = this@Manga.artist.takeIfValid()
        description = MarkdownDescription.Builder()
            .rating(rating?.takeIf { it > 0 }, max = 5)
            .description(this@Manga.description)
            .altTitles((listOfNotNull(titleAr) + altTitles).filterNot { it.equals(title, ignoreCase = true) })
            .build()
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
    private val number: Float,
    private val createdAt: String,
) {

    fun toSChapter(slug: String) = SChapter.create().apply {
        val numberText = number.toString().removeSuffix(".0")
        url = "$slug/$numberText"
        val baseName = "الفصل $numberText"
        name = if (title == baseName) baseName else "$baseName - $title"
        chapter_number = number
        date_upload = Instant.tryParse(createdAt)
        memo = buildJsonObject { put("id", id) }
    }
}

@Serializable
class PagesBox(
    val box: Box,
)

@Serializable
class Box(
    private val i: String,
    private val d: String,
) {
    fun decrypt(token: String): List<String> {
        val key = "$BOX_KEY|${token.takeLast(24)}".sha256()
        val iv = Base64.decode(i, Base64.DEFAULT)
        val plaintext = Base64.decode(d, Base64.DEFAULT).aesDecrypt(aesGcm(key, iv))
        return plaintext.decodeToString().parseAs<Pages>().pages
    }
}

private const val BOX_KEY = "1153b3c534368ded1f8386fe03beaa5ebb166a15f4275e25"

@Serializable
class Pages(
    val pages: List<String>,
)

@Serializable
class ApiError(
    val error: String? = null,
)

@Serializable
class Token(
    val token: String,
    val expiresIn: Int,
)
