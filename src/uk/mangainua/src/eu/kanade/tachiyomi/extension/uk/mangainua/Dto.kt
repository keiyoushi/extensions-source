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
        if (img.isEmpty()) return null

        return SManga.create().apply {
            title = name
            thumbnail_url = baseUrl + img
            url = baseUrl.toHttpUrl().resolve(this@SearchManga.url)!!.encodedPath
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
    fun toSChapter(baseUrl: String) = SChapter.create().apply {
        url = baseUrl.toHttpUrl().resolve(link)!!.encodedPath
        scanlator = team.takeIf(String::isNotEmpty)
        date_upload = date.times(1000L)
        chapter_number = num.toFloatOrNull() ?: -1f
        name = buildString {
            append("Том $tom Розділ $num")
            this@Chapter.name.takeIf(String::isNotEmpty)?.let { append(" $it") }
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
