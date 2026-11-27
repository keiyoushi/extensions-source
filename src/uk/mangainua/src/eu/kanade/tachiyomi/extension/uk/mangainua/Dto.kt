package eu.kanade.tachiyomi.extension.uk.mangainua

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl

@Serializable
class SearchWrapper(
    val pages: Int? = 1,
    val items: List<SearchManga>,
)

@Serializable
class SearchManga(
    private val id: Int,
    private val name: String,
    private val img: String,
    private val url: String,
) {
    fun toSManga(baseUrl: String): SManga? {
        // If there is no img, it's (probably) deleted manga, but site still shows it in search results
        // Example: https://manga.in.ua/mangas/?sort=name
        if (name == "." && img.isBlank()) return null

        return SManga.create().apply {
            title = name
            thumbnail_url = baseUrl + img
            url = if (this@SearchManga.url.startsWith("http")) {
                this@SearchManga.url.toHttpUrl().encodedPath
            } else {
                this@SearchManga.url
            }
            memo = buildJsonObject {
                put("id", id.toString())
            }
        }
    }
}

@Serializable
class ChaptersWrapper(
    val chapters: List<Chapter>,
)

@Serializable
class Chapter(
    private val tom: String,
    private val num: String,
    private val name: String,
    private val link: String,
    private val date: Int,
    private val team: String,
) {
    fun toSChapter() = SChapter.create().apply {
        url = if (link.startsWith("http")) {
            link.toHttpUrl().encodedPath
        } else {
            link
        }
        scanlator = team.takeIf(String::isNotBlank)
        date_upload = date.times(1000L)
        chapter_number = num.toFloatOrNull() ?: 0f
        name = buildString {
            append("Том $tom Розділ $num")
            this@Chapter.name.takeIf(String::isNotBlank)?.let { append(" $it") }
        }
    }
}

@Serializable
class GenreList(
    val genres: List<Genres>,
)

@Serializable
class Genres(
    val id: Int,
    val name: String,
)
