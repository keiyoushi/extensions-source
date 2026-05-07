package eu.kanade.tachiyomi.extension.uk.faust

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray

@Serializable
class SearchRequestBody(
    var searchQuery: String? = null,
    var page: Int? = null,
    var pageSize: Int = 30,
    var sortBy: String? = null,
    var mangaType: String? = null,
    var translationStatus: String? = null,
    var publicationStatus: String? = null,
    var ageBracket: String? = null,
    var yearFrom: String? = null,
    var yearTo: String? = null,
    var minChapters: String? = null,
    var maxChapters: String? = null,
    var genreIds: JsonArray? = null,
    var excludeGenreIds: JsonArray? = null,
    var tagIds: JsonArray? = null,
    var excludeTagIds: JsonArray? = null,
)

@Serializable
class SearchResponseDto(
    val page: Int = 0,
    val totalPages: Int = 0,
    val titles: List<SearchResponseTitlesDto>,
)

@Serializable
class SearchResponseTitlesDto(
    val name: String,
    val slug: String,
    val coverImageUrl: String,
)

@Serializable
class SMangaDto(
    val name: String,
    val coverImageUrl: String,
    val description: String? = null,
    val slug: String,
    val artists: List<Person>? = emptyList(),
    val authors: List<Person>? = emptyList(),
    val mangaType: String? = null,
    val tags: List<Tags>? = emptyList(),
    val genres: List<Tags>? = emptyList(),
    val translationStatus: String? = null,
    val averageRating: Float? = 0.0F,
    val bookmarksCount: Int? = 0,
    val englishName: String? = "",
    val votesCount: Int? = 0,
)

@Serializable
class Person(
    val firstName: String? = null,
    val lastName: String? = null,
)

@Serializable
class Tags(
    val name: String,
)

@Serializable
class ChapterResponseDto(
    val slug: String,
    val volumes: List<VolumesListDto>,
)

@Serializable
class VolumesListDto(
    val chapters: List<ChaptersListDto>,
)

@Serializable
class ChaptersListDto(
    val name: String,
    val slug: String,
    val volumeOrder: Float,
    val number: Float,
    val updatedDate: String? = null,
    val translationTeams: List<Tags>? = emptyList(),
)

@Serializable
class ChapterResponseList(
    val pages: List<ResponseImagesList>,
)

@Serializable
class ResponseImagesList(
    val blobName: String,
    val pageNumber: Int,
)

@Serializable
class GenreListPageDto(
    val items: List<GenreDto>,
)

@Serializable
class GenreDto(
    val id: String,
    val name: String,
)
