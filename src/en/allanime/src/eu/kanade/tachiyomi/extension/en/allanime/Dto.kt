package eu.kanade.tachiyomi.extension.en.allanime

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

typealias ApiPopularResponse = Data<PopularData>

typealias ApiSearchResponse = Data<SearchData>

typealias ApiMangaDetailsResponse = Data<MangaDetailsData>

typealias ApiChapterListResponse = Data<ChapterListData>

typealias ApiPageListResponse = Data<PageListData>

@Serializable
class Data<T>(val data: T)

@Serializable
class Edges<T>(val edges: List<T>)

// Popular
@Serializable
class PopularData(
    @SerialName("queryPopular") val popular: PopularMangas,
)

@Serializable
class PopularMangas(
    @SerialName("recommendations") val mangas: List<PopularManga>,
)

@Serializable
class PopularManga(
    @SerialName("anyCard") val manga: SearchManga? = null,
)

// Search
@Serializable
class SearchData(
    val mangas: Edges<SearchManga>,
)

@Serializable
class SearchManga(
    @SerialName("_id") val id: String,
    private val name: String,
    private val thumbnail: String? = null,
    private val englishName: String? = null,
) {
    fun toSManga() = SManga.create().apply {
        title = englishName ?: name
        url = "/manga/$id/${name.titleToSlug()}"
        thumbnail_url = thumbnail?.parseThumbnailUrl()
    }
}

// Details
@Serializable
class MangaDetailsData(
    val manga: Manga,
)

@Serializable
class Manga(
    @SerialName("_id") val id: String,
    private val name: String,
    private val thumbnail: String? = null,
    private val description: String? = null,
    private val authors: List<String>? = emptyList(),
    private val genres: List<String>? = emptyList(),
    private val tags: List<String>? = emptyList(),
    private val status: String? = null,
    private val altNames: List<String>? = emptyList(),
    private val englishName: String? = null,
) {
    fun toSManga() = SManga.create().apply {
        title = englishName ?: name
        url = "/manga/$id/${name.titleToSlug()}"
        thumbnail_url = thumbnail?.parseThumbnailUrl()
        description = this@Manga.description?.parseDescription()
        if (!altNames.isNullOrEmpty()) {
            if (description.isNullOrEmpty()) {
                description = "Alternative Titles:\n"
            } else {
                description += "\n\nAlternative Titles:\n"
            }

            description += altNames.joinToString("\n") { "• ${it.trim()}" }
        }
        if (authors?.isNotEmpty() == true) {
            author = authors.first().trim()
            artist = author
        }
        genre = ((genres ?: emptyList()) + (tags ?: emptyList()))
            .joinToString { it.trim() }
        status = this@Manga.status.parseStatus()
    }
}

// chapters details
@Serializable
class ChapterListData(
    @SerialName("episodeInfos") val chapterList: List<ChapterData>? = emptyList(),
)

@Serializable
class ChapterData(
    @SerialName("episodeIdNum") val chapterNum: JsonPrimitive,
    @SerialName("notes") val title: String? = null,
    private val uploadDates: DateDto? = null,
) {
    fun toSChapter(mangaUrl: String) = SChapter.create().apply {
        name = "Chapter $chapterNum"
        if (!title.isNullOrEmpty() && !title.contains(numberRegex)) {
            name += ": $title"
        }
        url = "/read/$mangaUrl/chapter-$chapterNum-sub"
        date_upload = uploadDates?.sub.parseDate()
    }

    companion object {
        private val numberRegex by lazy { Regex("\\d") }
    }
}

@Serializable
class DateDto(
    val sub: String? = null,
)

// page list
@Serializable
class PageListData(
    @SerialName("chapterPages") val pageList: Edges<Servers>?,
)

@Serializable
class Servers(
    @SerialName("pictureUrlHead") val serverUrl: String? = null,
    val pictureUrls: List<PageUrl>?,
)

@Serializable
class PageUrl(
    val url: String,
)
