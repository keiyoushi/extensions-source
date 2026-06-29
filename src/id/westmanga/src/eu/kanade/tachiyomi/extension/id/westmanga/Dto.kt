package eu.kanade.tachiyomi.extension.id.westmanga

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup

@Serializable
class Data<T>(
    val data: T,
)

@Serializable
class PaginatedData<T>(
    val data: List<T>,
    val paginator: Paginator,
)

@Serializable
class Paginator(
    @SerialName("current_page") private val current: Int,
    @SerialName("last_page") private val last: Int,
) {
    fun hasNextPage() = current < last
}

@Serializable
class BrowseManga(
    private val title: String,
    private val slug: String,
    private val cover: String? = null,
) {
    fun toSManga() = SManga.create().apply {
        // old urls compatibility
        url = "/manga/$slug/"
        title = this@BrowseManga.title
        thumbnail_url = this@BrowseManga.cover
    }
}

@Serializable
class Manga(
    private val title: String,
    private val slug: String,
    @SerialName("alternative_name") private val alternativeName: String? = null,
    @SerialName("sinopsis") private val synopsis: String? = null,
    private val cover: String? = null,
    private val author: String? = null,
    @SerialName("country_id") private val country: String? = null,
    private val status: String? = null,
    private val color: Boolean? = null,
    private val genres: List<Genre>,
    val chapters: List<Chapter>,
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        // old urls compatibility
        url = "/manga/$slug/"
        title = this@Manga.title
        thumbnail_url = this@Manga.cover
        author = this@Manga.author
        status = when (this@Manga.status?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
        genre = buildList {
            when (this@Manga.country) {
                "JP" -> add("Manga")
                "CN" -> add("Manhua")
                "KR" -> add("Manhwa")
            }
            if (this@Manga.color == true) {
                add("Colored")
            }
            this@Manga.genres.forEach { add(it.name) }
        }.joinToString()

        description = buildString {
            this@Manga.synopsis?.let {
                append(Jsoup.parseBodyFragment(it, baseUrl).text())
            }
            this@Manga.alternativeName?.let {
                if (isNotEmpty()) append("\n\n")
                append("Alternative Name: ")
                append(it.trim())
            }
        }
    }
}

@Serializable
class Genre(
    val name: String,
)

@Serializable
class Chapter(
    private val slug: String,
    private val number: String,
    @SerialName("updated_at") private val updatedAt: Time,
) {
    fun toSChapter() = SChapter.create().apply {
        // old urls compatibility
        url = "/$slug/"
        name = "Chapter $number"
        date_upload = updatedAt.time * 1000
    }
}

@Serializable
class Time(
    val time: Long,
)

@Serializable
class ImageList(
    val images: List<String>,
)

@Serializable
class ApiGenre(
    val id: Int,
    val name: String,
    val slug: String,
)
