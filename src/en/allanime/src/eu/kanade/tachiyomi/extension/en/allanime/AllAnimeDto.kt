package eu.kanade.tachiyomi.extension.en.allanime

import eu.kanade.tachiyomi.extension.en.allanime.AllAnimeHelper.parseDate
import eu.kanade.tachiyomi.extension.en.allanime.AllAnimeHelper.parseDescription
import eu.kanade.tachiyomi.extension.en.allanime.AllAnimeHelper.parseStatus
import eu.kanade.tachiyomi.extension.en.allanime.AllAnimeHelper.parseThumbnailUrl
import eu.kanade.tachiyomi.extension.en.allanime.AllAnimeHelper.titleToSlug
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
data class Data<T>(val data: T)

@Serializable
data class Edges<T>(val edges: List<T>)

// Popular
@Serializable
data class PopularData(
    @SerialName("queryPopular") val popular: PopularMangas,
)

@Serializable
data class PopularMangas(
    @SerialName("recommendations") val mangas: List<PopularManga>,
)

@Serializable
data class PopularManga(
    @SerialName("anyCard") val manga: SearchManga? = null,
)

// Search
@Serializable
data class SearchData(
    val mangas: Edges<SearchManga>,
)

@Serializable
data class SearchManga(
    @SerialName("_id") val id: String,
    val name: String,
    val thumbnail: String? = null,
    val englishName: String? = null,
) {
    fun toSManga() = SManga.create().apply {
        title = englishName ?: name
        url = "/manga/$id/${name.titleToSlug()}"
        thumbnail_url = thumbnail?.parseThumbnailUrl()
    }
}

// Details
@Serializable
data class MangaDetailsData(
    val manga: Manga,
)

@Serializable
data class Manga(
    @SerialName("_id") val id: String,
    val name: String,
    val thumbnail: String? = null,
    val description: String? = null,
    val authors: List<String>? = emptyList(),
    val genres: List<String>? = emptyList(),
    val tags: List<String>? = emptyList(),
    val status: String? = null,
    val altNames: List<String>? = emptyList(),
    val englishName: String? = null,
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
data class ChapterListData(
    @SerialName("episodeInfos") val chapterList: List<ChapterData>? = emptyList(),
)

@Serializable
data class ChapterData(
    @SerialName("episodeIdNum") val chapterNum: JsonPrimitive,
    @SerialName("notes") val title: String? = null,
    val uploadDates: DateDto? = null,
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
data class DateDto(
    val sub: String? = null,
)

// page lsit
@Serializable
data class PageListData(
    @SerialName("chapterPages") val pageList: Edges<Servers>?,
)

@Serializable
data class Servers(
    @SerialName("pictureUrlHead") val serverUrl: String? = null,
    val pictureUrls: List<PageUrl>?,
)

@Serializable
data class PageUrl(
    val url: String,
)
