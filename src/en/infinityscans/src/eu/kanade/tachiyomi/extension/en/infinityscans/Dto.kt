package eu.kanade.tachiyomi.extension.en.infinityscans

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
class ResponseDto<T>(val result: T)

@Serializable
class SearchResultDto(
    val titles: List<SearchEntryDto>,
)

@Serializable
class RankingResultDto(
    val weekly: List<SearchEntryDto>,
)

@Serializable
class SearchRequestBody(val search: String)

@Serializable
class SearchEntryDto(
    val id: String,
    val name: String,
    val uri: String,
    val cover: String,
    val authors: String?,
    val genres: String?,
    val status: String?,
) {
    fun toSManga(cdnHost: String) = SManga.create().apply {
        title = name
        thumbnail_url = "https://$cdnHost/$cover"
        // comic/mangaId/manga-slug
        url = uri.substringAfter("/")
    }
}

@Serializable
class MangaDetailsDto(
    val name: String,
    val uri: String,
    val altNames: List<String>? = null,
    val description: DescriptionDto,
    val genres: List<GenreDto>,
    val authors: List<AuthorDto>,
    val status: String,
    val cover: String? = null,
)

@Serializable
class DescriptionDto(
    val content: List<DescriptionContent>,
)

@Serializable
class DescriptionContent(
    val content: List<DescriptionText>? = null,
)

@Serializable
class DescriptionText(
    val text: String = "",
)

@Serializable
class GenreDto(
    val name: String,
)

@Serializable
class AuthorDto(
    val name: String,
)

@Serializable
class FiltersDto(val genres: List<FilterDto>, val authors: List<FilterDto>)

@Serializable
class FilterDto(val id: String, val name: String)

@Serializable
class ChapterListDto(
    val chapters: List<ChapterEntryDto>,
    val total: Int,
)

@Serializable
class ChapterEntryDto(
    val groups: List<Group>?,
    val id: String,
    @SerialName("name")
    val title: String,
    val sequence: String,
    val uploaded: String,
) {
    fun toSChapter(mangaPath: String) = SChapter.create().apply {
        name = title

        // Things like prologues mess up the sequence number
        chapter_number = title.substringAfter("hapter ").toFloatOrNull() ?: sequence.toFloat()
        date_upload = Instant.parseOrNull(uploaded)?.toEpochMilliseconds() ?: 0L
        url = "$mangaPath/chapter/$id"
        scanlator = groups?.joinToString { it.name }
    }
}

@Serializable
class Group(
    val id: String,
    val name: String,
)

@Serializable
class PageEntryDto(
    val path: String,
)
