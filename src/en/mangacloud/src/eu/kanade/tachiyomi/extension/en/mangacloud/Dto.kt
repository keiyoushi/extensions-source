package eu.kanade.tachiyomi.extension.en.mangacloud

import android.app.Application
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.SimpleTimeZone

@Serializable
class Data<T>(
    val data: T,
)

@Serializable
class DataList<T>(
    val list: List<T>,
)

@Serializable
class BrowseManga(
    private val id: String,
    private val title: String,
    private val cover: Image,
) {
    fun toSManga() = SManga.create().apply {
        url = id
        title = this@BrowseManga.title
        thumbnail_url = "$CDN_URL/$id/${cover.id}.${cover.format}"
    }
}

@Serializable
class Image(
    val id: String,
    @SerialName("f") val format: String,
)

@Serializable
class Tag(
    val id: String,
    val name: String,
    val type: String,
)

@Serializable
class Manga(
    val id: String,
    private val title: String,
    @SerialName("alt_titles")
    private val altTitles: String? = null,
    @SerialName("nat_titles")
    private val nativeTitles: String? = null,
    private val description: String? = null,
    private val status: String,
    @SerialName("start_year")
    private val startYear: Int? = null,
    @SerialName("end_year")
    private val endYear: Int? = null,
    private val type: String? = null,
    private val authors: String? = null,
    private val artists: String? = null,
    @SerialName("official_raw")
    private val officialRaw: String? = null,
    @SerialName("official_english")
    private val officialEnglish: String? = null,
    private val links: Links,
    private val tags: List<Tag>,
    val chapters: List<Chapter>,
    private val cover: Image,
) {
    fun toSManga(): SManga {
        val app = Injekt.get<Application>().packageName
        val groupTags = listOf("eu.kanade.tachiyomi.sy", "app.komikku")
            .any { app.startsWith(it) }
        val markdownDescription = listOf("app.mihon", "eu.kanade.tachiyomi.sy", "app.komikku")
            .any { app.startsWith(it) }

        return SManga.create().apply {
            url = id
            title = this@Manga.title
            artist = artists?.split("•")
                ?.joinToString(transform = String::trim)
            author = authors?.split("•")
                ?.joinToString(transform = String::trim)
            description = buildString {
                this@Manga.description?.also { append(it.trim(), "\n\n") }

                startYear?.also { start ->
                    append("Year: ", start)
                    endYear?.also { end ->
                        append(" - ", end)
                    }
                    append("\n\n")
                }

                buildList {
                    altTitles?.split("•")
                        ?.mapTo(this, String::trim)
                    nativeTitles?.split("、")
                        ?.mapTo(this, String::trim)
                }.also {
                    if (it.isNotEmpty()) {
                        append("Alternative Name(s):\n")
                        it.forEach { name ->
                            append("- ", name, "\n")
                        }
                        append("\n")
                    }
                }

                if (markdownDescription) {
                    buildList {
                        officialRaw?.also { add("Official Raw" to it) }
                        officialEnglish?.also { add("Official English" to it) }
                        links.al?.also {
                            val url = "https://anilist.co/manga/$it"
                            add("Anilist" to url)
                        }
                        links.mal?.also {
                            val url = "https://myanimelist.net/manga/$it"
                            add("MyAnimeList" to url)
                        }
                        links.mu?.also {
                            val url = "https://www.mangaupdates.com/series/$it"
                            add("MangaUpdates" to url)
                        }
                        links.md?.also {
                            val url = "https://mangadex.org/title/$it"
                            add("MangaDex" to url)
                        }
                    }.also {
                        if (it.isNotEmpty()) {
                            append("Links:\n")
                            it.forEach { (name, link) ->
                                append("- [$name]($link)\n")
                            }
                        }
                    }
                }
            }.trim()
            genre = buildList {
                type?.also {
                    if (groupTags) {
                        add("Type:$it")
                    } else {
                        add(it)
                    }
                }
                tags.sortedBy { it.type }
                    .mapTo(this) { tag ->
                        if (groupTags) {
                            val group = tag.type.replaceFirstChar {
                                if (it.isLowerCase()) {
                                    it.titlecase(Locale.ROOT)
                                } else {
                                    it.toString()
                                }
                            }
                            "$group:${tag.name}"
                        } else {
                            tag.name
                        }
                    }
            }.joinToString()
            status = when (this@Manga.status) {
                "Ongoing" -> SManga.ONGOING
                "Completed" -> SManga.COMPLETED
                "Cancelled" -> SManga.CANCELLED
                "Hiatus" -> SManga.ON_HIATUS
                else -> SManga.UNKNOWN
            }
            thumbnail_url = "$CDN_URL/$id/${cover.id}.${cover.format}"
            initialized = true
        }
    }
}

@Serializable
class Links(
    val al: Int? = null,
    val mal: Int? = null,
    val md: String? = null,
    val mu: String? = null,
)

@Serializable
class Chapter(
    val id: String,
    val number: Float,
    val name: String? = null,
    @SerialName("created_date")
    private val createdDate: String,
) {
    val date get() = dateFormat.tryParse(createdDate)
}

private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH).apply {
    timeZone = SimpleTimeZone.getTimeZone("UTC")
}

@Serializable
data class ChapterUrl(
    val comicId: String,
    val chapterId: String,
)

@Serializable
class ChapterContent(
    val id: String,
    @SerialName("comic_id")
    val comicId: String,
    val images: List<Image>,
)
