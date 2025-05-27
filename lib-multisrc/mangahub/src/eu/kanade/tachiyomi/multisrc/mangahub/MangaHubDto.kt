package eu.kanade.tachiyomi.multisrc.mangahub

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

typealias ApiChapterPagesResponse = ApiResponse<ApiChapterData>
typealias ApiSearchResponse = ApiResponse<ApiSearchObject>
typealias ApiMangaDetailsResponse = ApiResponse<ApiMangaObject>

// Base classes
@Serializable
class ApiResponse<T>(
    val data: T,
)

@Serializable
class ApiResponseError(
    val errors: List<ApiErrorMessages>?,
)

@Serializable
class ApiErrorMessages(
    val message: String,
)

@Serializable
class PublicIPResponse(
    val ip: String,
)

// Chapter metadata (pages)
@Serializable
class ApiChapterData(
    val chapter: ApiChapter,
)

@Serializable
class ApiChapter(
    val pages: String,
    val mangaID: Int,
    @SerialName("number") val chapterNumber: Float,
    val manga: ApiMangaData,
)

@Serializable
class ApiChapterPages(
    @SerialName("p") val page: String,
    @SerialName("i") val images: List<String>,
)

// Search, Popular, Latest
@Serializable
class ApiSearchObject(
    val search: ApiSearchResults,
)

@Serializable
class ApiSearchResults(
    val rows: List<ApiMangaSearchItem>,
)

@Serializable
class ApiMangaSearchItem(
    val title: String,
    val slug: String,
    val image: String,
    val author: String,
    val latestChapter: Float,
    val genres: String,
)

// Manga Details, Chapters
@Serializable
class ApiMangaObject(
    val manga: ApiMangaData,
)

@Serializable
class ApiMangaData(
    val title: String?,
    val status: String?,
    val image: String?,
    val author: String?,
    val artist: String?,
    val genres: String?,
    val description: String?,
    val alternativeTitle: String?,
    val slug: String?,
    val chapters: List<ApiMangaChapterList>?,
)

@Serializable
class ApiMangaChapterList(
    val number: Float,
    val title: String,
    val date: String,
)
