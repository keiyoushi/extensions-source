package eu.kanade.tachiyomi.extension.all.mangapark

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup

typealias SearchResponse = Data<SearchComics>
typealias DetailsResponse = Data<ComicNode>
typealias ChapterListResponse = Data<ChapterList>
typealias PageListResponse = Data<ChapterPages>

@Serializable
class Data<T>(val data: T)

@Serializable
class Items<T>(val items: List<T>)

@Serializable
class SearchComics(
    @SerialName("get_searchComic") val searchComics: Items<Data<MangaParkComic>>,
)

@Serializable
class ComicNode(
    @SerialName("get_comicNode") val comic: Data<MangaParkComic>,
)

@Serializable
class MangaParkComic(
    private val id: String,
    private val name: String,
    private val altNames: List<String>? = null,
    private val authors: List<String>? = null,
    private val artists: List<String>? = null,
    private val genres: List<String>? = null,
    private val originalStatus: String? = null,
    private val uploadStatus: String? = null,
    private val summary: String? = null,
    @SerialName("urlCoverOri") private val cover: String? = null,
    private val urlPath: String,
) {
    fun toSManga() = SManga.create().apply {
        url = "$urlPath#$id"
        title = name
        thumbnail_url = cover
        author = authors?.joinToString()
        artist = artists?.joinToString()
        description = buildString {
            val desc = summary?.let { Jsoup.parse(it).text() }
            val names = altNames?.takeUnless { it.isEmpty() }
                ?.joinToString("\n") { "• ${it.trim()}" }

            if (desc.isNullOrEmpty()) {
                if (!names.isNullOrEmpty()) {
                    append("Alternative Names:\n", names)
                }
            } else {
                append(desc)
                if (!names.isNullOrEmpty()) {
                    append("\n\nAlternative Names:\n", names)
                }
            }
        }
        genre = genres?.joinToString { it.replace("_", " ").toCamelCase() }
        status = when (originalStatus) {
            "ongoing" -> SManga.ONGOING
            "completed" -> {
                if (uploadStatus == "ongoing") {
                    SManga.PUBLISHING_FINISHED
                } else {
                    SManga.COMPLETED
                }
            }
            "hiatus" -> SManga.ON_HIATUS
            "cancelled" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        initialized = true
    }

    companion object {
        private fun String.toCamelCase(): String {
            val result = StringBuilder(length)
            var capitalize = true
            for (char in this) {
                result.append(
                    if (capitalize) {
                        char.uppercase()
                    } else {
                        char.lowercase()
                    },
                )
                capitalize = char.isWhitespace()
            }
            return result.toString()
        }
    }
}

@Serializable
class ChapterList(
    @SerialName("get_comicChapterList") val chapterList: List<Data<MangaParkChapter>>,
)

@Serializable
class MangaParkChapter(
    private val id: String,
    @SerialName("dname") private val displayName: String,
    private val title: String? = null,
    private val dateCreate: Long? = null,
    private val dateModify: Long? = null,
    private val urlPath: String,
    private val srcTitle: String? = null,
    private val userNode: Data<Name>? = null,
    val dupChapters: List<Data<MangaParkChapter>> = emptyList(),
) {
    fun toSChapter() = SChapter.create().apply {
        url = "$urlPath#$id"
        name = buildString {
            append(displayName)
            title?.let { append(": ", it) }
        }
        date_upload = dateModify ?: dateCreate ?: 0L
        scanlator = userNode?.data?.name ?: srcTitle ?: "Unknown"
    }
}

@Serializable
class Name(val name: String)

@Serializable
class ChapterPages(
    @SerialName("get_chapterNode") val chapterPages: Data<ImageFiles>,
)

@Serializable
class ImageFiles(
    val imageFile: UrlList,
)

@Serializable
class UrlList(
    val urlList: List<String>,
)
