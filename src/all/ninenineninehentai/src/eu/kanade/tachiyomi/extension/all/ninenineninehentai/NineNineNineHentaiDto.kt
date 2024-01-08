package eu.kanade.tachiyomi.extension.all.ninenineninehentai

import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.Locale

typealias ApiDetailsResponse = Data<DetailsResponse>

typealias ApiPageListResponse = Data<PageList>

@Serializable
data class Data<T>(val data: T)

@Serializable
data class Edges<T>(val edges: List<T>)

interface BrowseResponse {
    val chapters: Edges<ChapterResponse>
}

@Serializable
data class PopularResponse(
    @SerialName("queryPopularChapters") override val chapters: Edges<ChapterResponse>,
) : BrowseResponse

@Serializable
data class SearchResponse(
    @SerialName("queryChapters") override val chapters: Edges<ChapterResponse>,
) : BrowseResponse

@Serializable
data class DetailsResponse(
    @SerialName("queryChapter") val details: ChapterResponse,
)

@Serializable
data class ChapterResponse(
    @SerialName("_id") val id: String,
    val name: String,
    val uploadDate: String? = null,
    val format: String? = null,
    val description: String? = null,
    val language: String? = null,
    val pages: Int? = null,
    @SerialName("firstPics") val cover: List<Url>? = emptyList(),
    val tags: List<Tag>? = emptyList(),
) {
    fun toSManga(shortTitle: Boolean) = SManga.create().apply {
        url = id
        title = if (shortTitle) name.replace(shortenTitleRegex, "").trim() else name
        thumbnail_url = cover?.firstOrNull()?.absUrl
        author = this@ChapterResponse.author
        artist = author
        genre = genres
        description = buildString {
            if (!this@ChapterResponse.description.isNullOrEmpty()) append(this@ChapterResponse.description.trim(), "\n\n")
            if (formatParsed != null) append("Format: ${formatParsed}\n")
            if (languageParsed != null) append("Language: $languageParsed\n")
            if (group != null) append("Group: $group\n")
            if (characters != null) append("Character(s): $characters\n")
            if (parody != null) append("Parody: $parody\n")
            if (magazine != null) append("Magazine: $magazine\n")
            if (pages != null) append("Pages: $pages\n")
        }
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        initialized = true
    }

    private val formatParsed = when (format) {
        "artistcg" -> "ArtistCG"
        "gamecg" -> "GameCG"
        "imageset" -> "ImageSet"
        else -> format?.capitalize()
    }

    private val languageParsed = when (language) {
        "en" -> "English"
        "jp" -> "Japanese"
        "cn" -> "Chinese"
        "es" -> "Spanish"
        else -> language
    }

    private val author = tags?.firstOrNull { it.tagType == "artist" }?.tagName?.capitalize()

    private val group = tags?.filter { it.tagType == "group" }
        ?.joinToString { it.tagName.capitalize() }
        ?.takeUnless { it.isEmpty() }

    private val characters = tags?.filter { it.tagType == "character" }
        ?.joinToString { it.tagName.capitalize() }
        ?.takeUnless { it.isEmpty() }

    private val parody = tags?.filter { it.tagType == "parody" }
        ?.joinToString { it.tagName.capitalize() }
        ?.takeUnless { it.isEmpty() }

    private val magazine = tags?.filter { it.tagType == "magazine" }
        ?.joinToString { it.tagName.capitalize() }
        ?.takeUnless { it.isEmpty() }

    private val genres = tags?.filterNot { it.tagType in filterTags }
        ?.joinToString { it.tagName.capitalize() }
        ?.takeUnless { it.isEmpty() }

    companion object {
        private val filterTags = listOf("artist", "group", "character", "parody", "magazine")
        private val shortenTitleRegex = Regex("""(\[[^]]*]|[({][^)}]*[)}])""")

        private fun String.capitalize(): String {
            return this.trim().split(" ").joinToString(" ") { word ->
                word.replaceFirstChar {
                    if (it.isLowerCase()) {
                        it.titlecase(
                            Locale.getDefault(),
                        )
                    } else {
                        it.toString()
                    }
                }
            }
        }
    }
}

@Serializable
data class Url(val url: String) {
    val absUrl get() = url.toAbsUrl()

    companion object {
        fun String.toAbsUrl(baseUrl: String = loUrl): String {
            return if (this.matches(urlRegex)) {
                this
            } else {
                baseUrl + this
            }
        }

        private const val loUrl = "https://127.0.0.1/"
        private val urlRegex = Regex("^https?://.*")
    }
}

@Serializable
data class Tag(
    val tagName: String,
    val tagType: String? = "genre",
)

@Serializable
data class PageList(
    @SerialName("queryChapter") val chapter: PageUrl,
)

@Serializable
data class PageUrl(
    @SerialName("_id") val id: String,
    @SerialName("pictureUrls") val pages: List<Pages?>? = emptyList(),
)

@Serializable
data class Pages(
    @SerialName("picCdn") val urlPart: String,
    @SerialName("pics") val qualityOriginal: List<Url>,
    @SerialName("picsM") val qualityMedium: List<Url?>? = emptyList(),
)
