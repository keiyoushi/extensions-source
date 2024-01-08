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
data class Data<T>(val data: T)

@Serializable
data class Items<T>(val items: List<T>)

@Serializable
data class SearchComics(
    @SerialName("get_searchComic") val searchComics: Items<Data<MangaParkComic>>,
)

@Serializable
data class ComicNode(
    @SerialName("get_comicNode") val comic: Data<MangaParkComic>,
)

@Serializable
data class MangaParkComic(
    val id: String,
    val name: String,
    val altNames: List<String>? = null,
    val authors: List<String>? = null,
    val artists: List<String>? = null,
    val genres: List<String>? = null,
    val originalStatus: String? = null,
    val uploadStatus: String? = null,
    val summary: String? = null,
    @SerialName("urlCoverOri") val cover: String? = null,
    val urlPath: String,
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
data class ChapterList(
    @SerialName("get_comicChapterList") val chapterList: List<Data<MangaParkChapter>>,
)

@Serializable
data class MangaParkChapter(
    val id: String,
    @SerialName("dname") val displayName: String,
    val title: String? = null,
    val dateCreate: Long? = null,
    val dateModify: Long? = null,
    val urlPath: String,
) {
    fun toSChapter() = SChapter.create().apply {
        url = "$urlPath#$id"
        name = buildString {
            append(displayName)
            title?.let { append(": ", it) }
        }
        date_upload = dateModify ?: dateCreate ?: 0L
    }
}

@Serializable
data class ChapterPages(
    @SerialName("get_chapterNode") val chapterPages: Data<ImageFiles>,
)

@Serializable
data class ImageFiles(
    val imageFile: UrlList,
)

@Serializable
data class UrlList(
    val urlList: List<String>,
)
